package es.us.dad.WAIT.entities;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class UserService {
  
  private final MySQLPool mySqlClient;
  
  public UserService(Vertx vertx) {
     
    MySQLConnectOptions connectOptions = new MySQLConnectOptions()
        .setPort(3306)
        .setHost("localhost")
        .setDatabase("wait")
        .setUser("root")
        .setPassword("Jo5ejr12J");
    
    
    PoolOptions poolOptions = new PoolOptions()
        .setMaxSize(30);
    
    this.mySqlClient = MySQLPool.pool(vertx, connectOptions, poolOptions);
  }
  
  public Future<Integer> createUser(String email, String passwordHash, String username, String phone) {
    Future<Integer> future = Future.future();
    long createdAt = System.currentTimeMillis();
    
    String sql = "INSERT INTO users (email, password_hash, username, phone_number) " +
                 "VALUES (?, ?, ?, ?)";
    
    Tuple params = Tuple.of(email, passwordHash, username, phone);
    
    mySqlClient.preparedQuery(sql, params, ar -> {
      if (ar.succeeded()) {
        
        mySqlClient.query("SELECT LAST_INSERT_ID() as user_id", idResult -> {
          if (idResult.succeeded()) {
            Row row = idResult.result().iterator().next();
            future.complete(row.getInteger("user_id"));
          } else {
            future.fail(idResult.cause());
          }
        });
      } else {
        future.fail(ar.cause());
      }
    });
    
    return future;
  }
  
  
  private JsonObject rowToUserJson(Row row) {
    return new JsonObject()
        .put("user_id", row.getInteger("user_id"))
        .put("email", row.getString("email"))
        .put("username", row.getString("username"))
        .put("password_hash", row.getString("password_hash"))
        .put("phone_number", row.getString("phone_number"));
  }
  
  
  
}