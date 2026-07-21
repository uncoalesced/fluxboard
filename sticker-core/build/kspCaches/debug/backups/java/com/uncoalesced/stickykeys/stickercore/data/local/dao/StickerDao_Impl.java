package com.uncoalesced.stickykeys.stickercore.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StickerDao_Impl implements StickerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<StickerEntity> __insertionAdapterOfStickerEntity;

  private final EntityDeletionOrUpdateAdapter<StickerEntity> __updateAdapterOfStickerEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteStickerById;

  public StickerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStickerEntity = new EntityInsertionAdapter<StickerEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `stickers` (`id`,`packId`,`categoryId`,`isFavourite`,`createdAt`,`mimeType`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StickerEntity entity) {
        statement.bindString(1, entity.getId());
        if (entity.getPackId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getPackId());
        }
        if (entity.getCategoryId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCategoryId());
        }
        final int _tmp = entity.isFavourite() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindString(6, entity.getMimeType());
      }
    };
    this.__updateAdapterOfStickerEntity = new EntityDeletionOrUpdateAdapter<StickerEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `stickers` SET `id` = ?,`packId` = ?,`categoryId` = ?,`isFavourite` = ?,`createdAt` = ?,`mimeType` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StickerEntity entity) {
        statement.bindString(1, entity.getId());
        if (entity.getPackId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getPackId());
        }
        if (entity.getCategoryId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCategoryId());
        }
        final int _tmp = entity.isFavourite() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindString(6, entity.getMimeType());
        statement.bindString(7, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteStickerById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stickers WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public void insertSticker(final StickerEntity sticker) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfStickerEntity.insert(sticker);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateSticker(final StickerEntity sticker) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfStickerEntity.handle(sticker);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteStickerById(final String id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteStickerById.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteStickerById.release(_stmt);
    }
  }

  @Override
  public Flow<List<StickerEntity>> getAllStickers() {
    final String _sql = "SELECT * FROM stickers ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stickers"}, new Callable<List<StickerEntity>>() {
      @Override
      @NonNull
      public List<StickerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackId = CursorUtil.getColumnIndexOrThrow(_cursor, "packId");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsFavourite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavourite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<StickerEntity> _result = new ArrayList<StickerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StickerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpPackId;
            if (_cursor.isNull(_cursorIndexOfPackId)) {
              _tmpPackId = null;
            } else {
              _tmpPackId = _cursor.getString(_cursorIndexOfPackId);
            }
            final String _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getString(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsFavourite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavourite);
            _tmpIsFavourite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            _item = new StickerEntity(_tmpId,_tmpPackId,_tmpCategoryId,_tmpIsFavourite,_tmpCreatedAt,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public StickerEntity getStickerById(final String id) {
    final String _sql = "SELECT * FROM stickers WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfPackId = CursorUtil.getColumnIndexOrThrow(_cursor, "packId");
      final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
      final int _cursorIndexOfIsFavourite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavourite");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
      final StickerEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpId;
        _tmpId = _cursor.getString(_cursorIndexOfId);
        final String _tmpPackId;
        if (_cursor.isNull(_cursorIndexOfPackId)) {
          _tmpPackId = null;
        } else {
          _tmpPackId = _cursor.getString(_cursorIndexOfPackId);
        }
        final String _tmpCategoryId;
        if (_cursor.isNull(_cursorIndexOfCategoryId)) {
          _tmpCategoryId = null;
        } else {
          _tmpCategoryId = _cursor.getString(_cursorIndexOfCategoryId);
        }
        final boolean _tmpIsFavourite;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsFavourite);
        _tmpIsFavourite = _tmp != 0;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final String _tmpMimeType;
        _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
        _result = new StickerEntity(_tmpId,_tmpPackId,_tmpCategoryId,_tmpIsFavourite,_tmpCreatedAt,_tmpMimeType);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Flow<List<StickerEntity>> getStickersByPack(final String packId) {
    final String _sql = "SELECT * FROM stickers WHERE packId = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stickers"}, new Callable<List<StickerEntity>>() {
      @Override
      @NonNull
      public List<StickerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackId = CursorUtil.getColumnIndexOrThrow(_cursor, "packId");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsFavourite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavourite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<StickerEntity> _result = new ArrayList<StickerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StickerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpPackId;
            if (_cursor.isNull(_cursorIndexOfPackId)) {
              _tmpPackId = null;
            } else {
              _tmpPackId = _cursor.getString(_cursorIndexOfPackId);
            }
            final String _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getString(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsFavourite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavourite);
            _tmpIsFavourite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            _item = new StickerEntity(_tmpId,_tmpPackId,_tmpCategoryId,_tmpIsFavourite,_tmpCreatedAt,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<StickerEntity>> getStickersByCategory(final String categoryId) {
    final String _sql = "SELECT * FROM stickers WHERE categoryId = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, categoryId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stickers"}, new Callable<List<StickerEntity>>() {
      @Override
      @NonNull
      public List<StickerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackId = CursorUtil.getColumnIndexOrThrow(_cursor, "packId");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsFavourite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavourite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<StickerEntity> _result = new ArrayList<StickerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StickerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpPackId;
            if (_cursor.isNull(_cursorIndexOfPackId)) {
              _tmpPackId = null;
            } else {
              _tmpPackId = _cursor.getString(_cursorIndexOfPackId);
            }
            final String _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getString(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsFavourite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavourite);
            _tmpIsFavourite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            _item = new StickerEntity(_tmpId,_tmpPackId,_tmpCategoryId,_tmpIsFavourite,_tmpCreatedAt,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<StickerEntity>> getFavouriteStickers() {
    final String _sql = "SELECT * FROM stickers WHERE isFavourite = 1 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stickers"}, new Callable<List<StickerEntity>>() {
      @Override
      @NonNull
      public List<StickerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackId = CursorUtil.getColumnIndexOrThrow(_cursor, "packId");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsFavourite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavourite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<StickerEntity> _result = new ArrayList<StickerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StickerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpPackId;
            if (_cursor.isNull(_cursorIndexOfPackId)) {
              _tmpPackId = null;
            } else {
              _tmpPackId = _cursor.getString(_cursorIndexOfPackId);
            }
            final String _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getString(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsFavourite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavourite);
            _tmpIsFavourite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            _item = new StickerEntity(_tmpId,_tmpPackId,_tmpCategoryId,_tmpIsFavourite,_tmpCreatedAt,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
