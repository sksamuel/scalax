package com.sksamuel.exts.sql

import java.sql.{Connection, PreparedStatement, ResultSet}

import com.sksamuel.exts.io.Using
import com.sksamuel.exts.jdbc.ResultSetIterator

class SQLSupport(connFn: () => Connection, fetchSize: Int = 100) extends Using {

  def batchInsert[T](ts: Seq[T], sql: String, batchSize: Int = 50)(indexer: T => Seq[Any]): Seq[Int] = {
    using(connFn()) { conn =>
      val stmt = conn.prepareStatement(sql)
      ts.grouped(batchSize).flatMap { batch =>
        stmt.clearBatch()
        for (t <- batch) {
          val params = indexer(t)
          params.zipWithIndex.foreach { case (param, index) => stmt.setObject(index + 1, param) }
          stmt.addBatch()
        }
        stmt.executeBatch()
      }.toVector
    }
  }

  def insert(table: String, fields: Seq[String], parameters: Seq[Any]): Int = {
    require(fields.size == parameters.size)
    val v = List.fill(fields.size)("?").mkString(",")
    val f = fields.mkString("`", "`,`", "`")
    insert(s"insert into $table ($f) values ($v)", parameters)
  }

  def insert(sql: String, parameters: Seq[Any]): Int = {
    insert(
      sql,
      stmt => parameters.zipWithIndex.foreach { case (param, index) =>
        stmt.setObject(index + 1, param)
      }
    )
  }

  def insert(sql: String, paramFn: PreparedStatement => Unit): Int = {
    using(connFn()) { conn =>
      val stmt = conn.prepareStatement(sql)
      paramFn(stmt)
      stmt.executeUpdate()
    }
  }

  def query[T](sql: String, parameters: Seq[Any] = Nil)(mapper: ResultSet => T): Seq[T] = {
    query(
      sql,
      stmt => parameters.zipWithIndex.foreach { case (param, index) =>
        stmt.setObject(index + 1, param)
      }
    )(mapper)
  }

  def query[T](sql: String, paramFn: PreparedStatement => Unit)(mapper: ResultSet => T): Seq[T] = {
    using(connFn()) { conn =>
      val stmt = conn.prepareStatement(sql)
      paramFn(stmt)
      val rs = stmt.executeQuery
      rs.setFetchSize(fetchSize)
      ResultSetIterator(rs).map(mapper).toVector
    }
  }
}