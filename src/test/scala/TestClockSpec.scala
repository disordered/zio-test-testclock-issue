import doobie.Transactor
import doobie.implicits.toSqlInterpolator
import doobie.implicits.toConnectionIOOps
import com.dimafeng.testcontainers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import zio.test.{assertTrue, live, testClockWith, TestAspect, ZIOSpecDefault}
import zio.{Clock, Random, Scope, Task, ULayer, URLayer, ZIO, ZLayer}
import zio.interop.catz.asyncInstance

import java.time.{Instant, YearMonth}

case class DBContainer(username: String, password: String, port: Int, host: String, engine: String)

object TestClockSpec extends ZIOSpecDefault {
  val mysqlContainer: ULayer[DBContainer] =
    ZLayer.scoped {
      for {
        container <-
          ZIO.succeed(
            MySQLContainer
              .Def(
                dockerImageName = DockerImageName.parse("mysql:8.0.32"),
                username = "root",
                password = "example",
              )
              .createContainer(),
          )
        _ <- ZIO.succeed(container.start())
        _ <- ZIO.serviceWithZIO[Scope](_.addFinalizer(ZIO.succeed(container.stop())))
      } yield DBContainer(
        username = container.username,
        password = container.password,
        port = container.mappedPort(3306),
        host = container.host,
        engine = "mysql",
      )
    }

  val transactorLayer: URLayer[DBContainer, Transactor[Task]] = ZLayer.fromFunction(getTransactor)

  private def getJdbcProps(container: DBContainer) = {
    val props = java.util.Properties()
    props.put("user", container.username)
    props.put("password", container.password)
    props.put("connectionTimeZone", "UTC")
    props.put("forceConnectionTimeZoneToSession", true)
    props
  }

  private def getTransactor(container: DBContainer): Transactor[Task] = Transactor.fromDriverManager[Task](
    driver = "com.mysql.cj.jdbc.Driver",
    url = s"jdbc:${container.engine}://${container.host}:${container.port}/?connectTimeout=2000",
    getJdbcProps(container)
  )

  private def setupSchema() =
    for {
      trx <- ZIO.service[Transactor[Task]]
      _ <- sql"create database mytest".update.run.transact(trx)
      _ <- sql"create table mytest.mytable(id int not null auto_increment, test_value varchar(255) not null, primary key (id))"
        .update.run.transact(trx)
    } yield ()

  override def spec =
    (suite("Test clock")(
      (1 to 100).map { idx =>
        test(s"Test $idx") {
          for {
            trx <- ZIO.service[Transactor[Task]]
            testValue <- live(Random.nextUUID).map(_.toString)
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- testClockWith(_.setTime(Instant.EPOCH.plusSeconds(1)))
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            day1 <- testClockWith(_.instant)
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- testClockWith(_.setTime(Instant.parse("1970-01-02T00:00:00.00Z")))
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            day2 <- testClockWith(_.instant)
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- testClockWith(_.setTime(Instant.parse("1970-02-15T00:00:00.00Z")))
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- Clock.javaClock.map(YearMonth.now)
            _ <- Clock.instant
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- testClockWith(_.setTime(Instant.parse("1970-03-15T00:00:00.00Z")))
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
            _ <- Clock.javaClock.map(YearMonth.now)
            _ <- Clock.instant
            _ <- sql"insert into mytest.mytable(test_value) values($testValue)".update.run.transact(trx)
          } yield assertTrue(true)
        }
      },
    ) @@ TestAspect.beforeAll(setupSchema())
  ).provideSomeLayerShared(mysqlContainer >+> transactorLayer)
}