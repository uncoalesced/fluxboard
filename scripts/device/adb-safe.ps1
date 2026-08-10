# Engineered by uncoalesced
#
# Device helpers that refuse the traps this project keeps falling into.
#
# `CLAUDE.md` has carried a warning about force-stopping the IME for several releases. It was
# still tripped during the 2026-08-06 device pass, at the very end, while tidying up -- which
# is the point: a warning in a document is read at the start of a session and the mistake is
# made at the end of one. This file makes the guard mechanical instead.
#
# Dot-source it before driving a device:
#
#     . scripts/device/adb-safe.ps1
#     Stop-AppSafely com.example.someapp
#
# Every function here is a thin wrapper. Nothing is cached, because the bound IME can change
# between two commands and a stale answer is exactly the failure being prevented.

<#
.SYNOPSIS
Returns the package name of the currently selected input method, or $null.
#>
function Get-BoundImePackage {
    $id = (adb shell settings get secure default_input_method) 2>$null
    if ([string]::IsNullOrWhiteSpace($id) -or $id -eq 'null') { return $null }
    # The id is "package/.ServiceClass" -- everything before the slash is the package.
    return ($id.Trim() -split '/')[0]
}

<#
.SYNOPSIS
Force-stops a package, unless it is the keyboard currently bound to the system.

.DESCRIPTION
Force-stopping the bound IME makes the system immediately switch the user to a different
keyboard. Two things then go wrong at once, and the second is worse than the first: the device
is left on somebody else's keyboard, and every screenshot taken afterwards is of that keyboard
rather than the build under test. AOSP LatinIME looks close enough to this one at a glance that
measurements have been recorded against it without anyone noticing.

Pass -Force to override, which is occasionally legitimate -- uninstalling, or deliberately
testing the rebind. The override is loud on purpose.
#>
function Stop-AppSafely {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [switch]$Force
    )

    $bound = Get-BoundImePackage
    if ($bound -eq $Package -and -not $Force) {
        Write-Warning "REFUSED: '$Package' is the bound IME (default_input_method)."
        Write-Warning "Force-stopping it switches the device to another keyboard, and every"
        Write-Warning "screenshot after that is of that keyboard, not this build."
        Write-Warning "If the app must be restarted, hide the keyboard and re-focus a field,"
        Write-Warning "or reinstall over the top -- both keep the binding. Use -Force only if"
        Write-Warning "you intend the rebind, and re-select the IME afterwards with:"
        Write-Warning "  Restore-Ime '<package>/<service>'"
        return $false
    }

    if ($bound -eq $Package) {
        Write-Warning "-Force given: stopping the bound IME '$Package'. The system will switch"
        Write-Warning "keyboards. Re-select it with Restore-Ime before measuring anything."
    }

    adb shell am force-stop $Package | Out-Null
    return $true
}

<#
.SYNOPSIS
Clears an app's data, unless it is the keyboard currently bound to the system.

.DESCRIPTION
`pm clear` is the same trap as `am force-stop` wearing a different name, and it was found the
same way -- by tripping it. It force-stops the package as part of clearing, so the system
switches keyboards, and it additionally wipes the very storage you were probably about to
inspect: the personal dictionary, the clipboard history, custom themes and layouts. Confirm
what is in `files/` and `databases/` before reaching for it.

Pass -Force when the wipe is the intent. Re-select the IME afterwards with Restore-Ime; this
function does not do it for you, because the caller knows the full service id and this one
only knows the package.
#>
function Clear-AppDataSafely {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [switch]$Force
    )

    $bound = Get-BoundImePackage
    if ($bound -eq $Package -and -not $Force) {
        Write-Warning "REFUSED: 'pm clear $Package' would wipe the bound IME."
        Write-Warning "It force-stops the package (switching the device to another keyboard)"
        Write-Warning "and deletes the dictionary, clipboard history, themes and layouts."
        Write-Warning "Pull anything you need first -- after the clear it is unrecoverable."
        return $false
    }

    adb shell pm clear $Package | Out-Null
    if ($bound -eq $Package) {
        Write-Warning "Cleared the bound IME. Re-select it now with Restore-Ime."
    }
    return $true
}

<#
.SYNOPSIS
Re-enables and re-selects an input method after it has been unbound.

.NOTES
The id needs the full service path, and for this project that is
com.uncoalesced.stickykeys/.keyboardcore.ime.StickyKeysIME -- note `.keyboardcore.`, because
the service lives in that module. The shorter /.ime.StickyKeysIME is rejected.
#>
function Restore-Ime {
    param([Parameter(Mandatory = $true)][string]$ImeId)
    adb shell ime enable $ImeId | Out-Null
    adb shell ime set $ImeId | Out-Null
    $now = (adb shell settings get secure default_input_method).Trim()
    if ($now -ne $ImeId) {
        Write-Warning "Re-selecting '$ImeId' did not take. default_input_method is '$now'."
        return $false
    }
    Write-Output "default_input_method = $now"
    return $true
}

<#
.SYNOPSIS
Fails loudly if the IME under test is not the one currently bound.

.DESCRIPTION
Call this before a screenshot or a measurement. It is the cheap version of the check that
would have caught a whole round of readings taken against the wrong keyboard.
#>
function Assert-ImeBound {
    param([Parameter(Mandatory = $true)][string]$Package)
    $bound = Get-BoundImePackage
    if ($bound -ne $Package) {
        throw "Expected '$Package' to be the bound IME, but it is '$bound'. " +
            "Anything measured now describes a different keyboard."
    }
}
