package com.uncoalesced.stickykeys.stickercore.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao_Impl;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao_Impl;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StickyKeysDatabase_Impl extends StickyKeysDatabase {
  private volatile StickerDao _stickerDao;

  private volatile PackDao _packDao;

  private volatile CategoryDao _categoryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stickers` (`id` TEXT NOT NULL, `packId` TEXT, `categoryId` TEXT, `isFavourite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dbc7059c4b032ef18c509aa3a0d2cb34')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `stickers`");
        db.execSQL("DROP TABLE IF EXISTS `packs`");
        db.execSQL("DROP TABLE IF EXISTS `categories`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsStickers = new HashMap<String, TableInfo.Column>(6);
        _columnsStickers.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStickers.put("packId", new TableInfo.Column("packId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStickers.put("categoryId", new TableInfo.Column("categoryId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStickers.put("isFavourite", new TableInfo.Column("isFavourite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStickers.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStickers.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStickers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStickers = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStickers = new TableInfo("stickers", _columnsStickers, _foreignKeysStickers, _indicesStickers);
        final TableInfo _existingStickers = TableInfo.read(db, "stickers");
        if (!_infoStickers.equals(_existingStickers)) {
          return new RoomOpenHelper.ValidationResult(false, "stickers(com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity).\n"
                  + " Expected:\n" + _infoStickers + "\n"
                  + " Found:\n" + _existingStickers);
        }
        final HashMap<String, TableInfo.Column> _columnsPacks = new HashMap<String, TableInfo.Column>(4);
        _columnsPacks.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPacks.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPacks.put("author", new TableInfo.Column("author", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPacks.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPacks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPacks = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPacks = new TableInfo("packs", _columnsPacks, _foreignKeysPacks, _indicesPacks);
        final TableInfo _existingPacks = TableInfo.read(db, "packs");
        if (!_infoPacks.equals(_existingPacks)) {
          return new RoomOpenHelper.ValidationResult(false, "packs(com.uncoalesced.stickykeys.stickercore.data.local.entity.PackEntity).\n"
                  + " Expected:\n" + _infoPacks + "\n"
                  + " Found:\n" + _existingPacks);
        }
        final HashMap<String, TableInfo.Column> _columnsCategories = new HashMap<String, TableInfo.Column>(3);
        _columnsCategories.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("sortOrder", new TableInfo.Column("sortOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCategories = new TableInfo("categories", _columnsCategories, _foreignKeysCategories, _indicesCategories);
        final TableInfo _existingCategories = TableInfo.read(db, "categories");
        if (!_infoCategories.equals(_existingCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "categories(com.uncoalesced.stickykeys.stickercore.data.local.entity.CategoryEntity).\n"
                  + " Expected:\n" + _infoCategories + "\n"
                  + " Found:\n" + _existingCategories);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "dbc7059c4b032ef18c509aa3a0d2cb34", "535ffd24e5d533e7b05adca4f7666e7e");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "stickers","packs","categories");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `stickers`");
      _db.execSQL("DELETE FROM `packs`");
      _db.execSQL("DELETE FROM `categories`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(StickerDao.class, StickerDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PackDao.class, PackDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CategoryDao.class, CategoryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public StickerDao stickerDao() {
    if (_stickerDao != null) {
      return _stickerDao;
    } else {
      synchronized(this) {
        if(_stickerDao == null) {
          _stickerDao = new StickerDao_Impl(this);
        }
        return _stickerDao;
      }
    }
  }

  @Override
  public PackDao packDao() {
    if (_packDao != null) {
      return _packDao;
    } else {
      synchronized(this) {
        if(_packDao == null) {
          _packDao = new PackDao_Impl(this);
        }
        return _packDao;
      }
    }
  }

  @Override
  public CategoryDao categoryDao() {
    if (_categoryDao != null) {
      return _categoryDao;
    } else {
      synchronized(this) {
        if(_categoryDao == null) {
          _categoryDao = new CategoryDao_Impl(this);
        }
        return _categoryDao;
      }
    }
  }
}
