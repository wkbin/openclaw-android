package com.openclaw.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 占位表：Room 要求数据库至少包含一个实体，本表不承载任何业务数据。 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey val id: Int = 0,
    val value: String? = null,
)
