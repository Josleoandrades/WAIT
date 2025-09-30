package es.us.dad.WAIT.rest;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import es.us.dad.WAIT.entities.Actuator;
import es.us.dad.WAIT.entities.ActuatorStatus;
import es.us.dad.WAIT.entities.Device;
import es.us.dad.WAIT.entities.SensorAC;
import es.us.dad.WAIT.entities.SensorGps;
import es.us.dad.WAIT.entities.sensorACStates;
import es.us.dad.WAIT.entities.sensorGpsStates;
import es.us.dad.WAIT.entities.LatLong;
import es.us.dad.WAIT.entities.UserService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.security.MessageDigest;
import java.security.Security;
import java.sql.Timestamp;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.mindrot.jbcrypt.BCrypt;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class RestAPIVerticle extends AbstractVerticle {

	private transient Gson gson;
	MySQLPool mySqlClient;
	
	private UserService userService;
	

	@Override
	public void start(Promise<Void> startFuture) {
		Security.addProvider(new BouncyCastleProvider());
		
		
		MySQLConnectOptions connectOptions = new MySQLConnectOptions().setPort(3306).setHost("localhost")
				.setDatabase("wait").setUser("root").setPassword("Jo5ejr12J");

		PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

		mySqlClient = MySQLPool.pool(vertx, connectOptions, poolOptions);

		// Instantiating a Gson serialize object using specific date format
		gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();

		// Defining the router object
		Router router = Router.router(vertx);

		// Handling any server startup result
		HttpServer httpServer = vertx.createHttpServer();
		httpServer.requestHandler(router::handle).listen(8080, "0.0.0.0",result -> {
			if (result.succeeded()) {
				System.out.println("API Rest is listening on port 8080");
				startFuture.complete();
			} else {
				startFuture.fail(result.cause());
			}
		});
		
		
		
		
	    userService = new UserService(vertx);

		// Defining URI paths for each method in RESTful interface, including body
		// handling
		router.route("/api*").handler(BodyHandler.create());

		// Endpoint definition
		
		//SensorsAC
		router.get("/api/sensorsAc/:sensorAc").handler(this::getSensorAcById); //OK
		router.post("/api/sensorsAc").handler(this::addSensorAc); //OK
		router.delete("/api/sensorsAc/:sensorAcid").handler(this::deleteSensorAc); //OK
		
		
		//SesnorACStates
		router.get("/api/sensorsAcStates/:sensorAcState").handler(this::getSensorAcStateById); //OK
		router.post("/api/sensorsAcStates").handler(this::addSensorAcState); //OK
		router.delete("/api/sensorsAcStates/:sensorAcStatesid").handler(this::deleteSensorAcState); //OK
		
		
		//SensorsGps
		router.get("/api/sensorsGps/:sensorGps").handler(this::getSensorGpsById); //OK
		router.post("/api/sensorsGps").handler(this::addSensorGps); //OK
		router.delete("/api/sensorsGps/:sensorGpsid").handler(this::deleteSensorGps); //OK
		
		
		//SensorsGpsStates
		router.get("/api/sensorsGpsStates/:sensorGpsStates").handler(this::getSensorGpsStatesById); //OK
		router.post("/api/sensorsGpsStates").handler(this::addSensorGpsStates); //OK
		router.delete("/api/sensorsGpsStates/:sensorGpsStatesid").handler(this::deleteSensorGpsStates); //OK
		
		
		//Usuarios 
		
		// Ruta para registro
	    router.post("/api/register").handler(this::registerHandler);
	    
	    

		
		//Devices
		router.get("/api/devices/:device").handler(this::getDeviceById); //OK
		router.post("/api/devices").handler(this::addDevice); //OK
		router.delete("/api/devices/:deviceid").handler(this::deleteDevice); //OK
		router.get("/api/devices/:deviceid/sensorsGpsStates").handler(this::getSensorsGpsStatesFromDevice); //OK
		router.get("/api/devices/:deviceid/actuators").handler(this::getActuatorsFromDevice); //OK
		
		
		
		//Actuadores
		router.get("/api/actuators/:actuator").handler(this::getActuatorById); //OK
		router.post("/api/actuators").handler(this::addActuator); //OK
		router.delete("/api/actuators/:actuatorid").handler(this::deleteActuator); //OK
		//router.put("/api/actuators/:actuatorid").handler(this::putActuator); //OK
		
		
		//ActuadorValues
		router.post("/api/actuator_states").handler(this::addActuatorStatus); //OK
		router.delete("/api/actuator_states/:actuatorstatusid").handler(this::deleteActuatorStatus); //OK
		router.get("/api/actuator_states/:actuatorid/last").handler(this::getLastActuatorStatus); //OK
		
		//Device y user
		router.get("/api/users/:userId/devices").handler(this::getUserDevices); //OK
		router.post("/api/add-device").handler(this::addDeviceUser);
		router.post("/api/login").handler(this::loginHandler);
		router.get("/api/devices/:deviceId/sensorsAcStates").handler(this::getSensorsAcStatesFromDevices);
		
	}

	//Device y user 
	
	private void getUserDevices(RoutingContext routingContext) {
	    mySqlClient.getConnection(connection -> {
	        int userId = Integer.parseInt(routingContext.request().getParam("userId"));
	        if (connection.succeeded()) {
	            String sql = "SELECT d.idDevice, d.name, du.nickname, du.permission_level " +
	                        "FROM devices d " +
	                        "JOIN devices_users du ON d.idDevice = du.idDevice " +
	                        "WHERE du.user_id = ?";
	            
	            connection.result().preparedQuery(sql, Tuple.of(userId), res -> {
	                if (res.succeeded()) {
	                    // Get the result set
	                    RowSet<Row> resultSet = res.result();
	                    System.out.println("Dispositivos encontrados: " + resultSet.size());
	                    
	                    // Usar List y luego convertir a JSON String manualmente
	                    List<JsonObject> devicesList = new ArrayList<>();
	                    
	                    for (Row elem : resultSet) {
	                        // Crear cada dispositivo como JsonObject simple
	                        JsonObject device = new JsonObject();
	                        device.put("device_id", elem.getInteger("idDevice")); 
	                        device.put("name", elem.getString("name"));
	                        device.put("nickname", elem.getString("nickname"));
	                        device.put("permission_level", elem.getInteger("permission_level"));
	                        
	                        // Añadir a la lista
	                        devicesList.add(device);
	                        
	                        System.out.println("Dispositivo añadido: " + device.toString());
	                    }
	                    
	                    // Convertir la lista a JSON Array string manualmente
	                    StringBuilder jsonBuilder = new StringBuilder();
	                    jsonBuilder.append("[");
	                    
	                    for (int i = 0; i < devicesList.size(); i++) {
	                        jsonBuilder.append(devicesList.get(i).toString());
	                        if (i < devicesList.size() - 1) {
	                            jsonBuilder.append(",");
	                        }
	                    }
	                    
	                    jsonBuilder.append("]");
	                    String jsonResult = jsonBuilder.toString();
	                    
	                    System.out.println("JSON final: " + jsonResult);
	                    
	                    routingContext.response()
	                        .putHeader("content-type", "application/json; charset=utf-8")
	                        .setStatusCode(200)
	                        .end(jsonResult);
	                } else {
	                    System.out.println("Error: " + res.cause().getLocalizedMessage());
	                    routingContext.response().setStatusCode(500)
	                        .end("Error al obtener los dispositivos: " + res.cause().getMessage());
	                }
	                // Cerramos la conexión después de obtener los resultados
	                connection.result().close();
	            });
	        } else {
	            System.out.println(connection.cause().toString());
	            routingContext.response().setStatusCode(500)
	                .end("Error con la conexión: " + connection.cause().getMessage());
	        }
	    });
	}
	
	
	private void getSensorsAcStatesFromDevices(RoutingContext routingContext) {
	    mySqlClient.getConnection(connection -> {
	        int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
	        if (connection.succeeded()) {
	            // Consulta siguiendo el mismo patrón que getUserDevices
	            String sql = "SELECT acs.idsensorsACStates, acs.idSensorAC, acs.valueAc, acs.valueGir, acs.removed " +
	                        "FROM sensorsacstates acs " +
	                        "JOIN sensorsac ac ON acs.idSensorAC = ac.idSensorsAC " +
	                        "WHERE ac.idDevice = ? AND acs.removed = 0 " +
	                        "ORDER BY acs.idsensorsACStates DESC " +
	                        "LIMIT 10";
	            
	            connection.result().preparedQuery(sql, Tuple.of(deviceId), res -> {
	                if (res.succeeded()) {
	                    // Get the result set
	                    RowSet<Row> resultSet = res.result();
	                    System.out.println("Estados de sensores encontrados: " + resultSet.size());
	                    
	                    // Usar List y luego convertir a JSON String manualmente (IGUAL que getUserDevices)
	                    List<JsonObject> sensorsList = new ArrayList<>();
	                    
	                    for (Row elem : resultSet) {
	                        // Crear cada estado de sensor como JsonObject simple
	                        JsonObject sensorState = new JsonObject();
	                        sensorState.put("idsensorsACStates", elem.getInteger("idsensorsACStates"));
	                        sensorState.put("idSensorAC", elem.getInteger("idSensorAC"));
	                        sensorState.put("valueAc", elem.getInteger("valueAc"));
	                        sensorState.put("valueGir", elem.getInteger("valueGir"));
	                        sensorState.put("removed", elem.getBoolean("removed"));
	                        
	                        // Añadir a la lista
	                        sensorsList.add(sensorState);
	                        
	                        System.out.println("Estado de sensor añadido: " + sensorState.toString());
	                    }
	                    
	                    // Convertir la lista a JSON Array string manualmente (IGUAL que getUserDevices)
	                    StringBuilder jsonBuilder = new StringBuilder();
	                    jsonBuilder.append("[");
	                    
	                    for (int i = 0; i < sensorsList.size(); i++) {
	                        jsonBuilder.append(sensorsList.get(i).toString());
	                        if (i < sensorsList.size() - 1) {
	                            jsonBuilder.append(",");
	                        }
	                    }
	                    
	                    jsonBuilder.append("]");
	                    String jsonResult = jsonBuilder.toString();
	                    
	                    System.out.println("JSON final: " + jsonResult);
	                    
	                    routingContext.response()
	                        .putHeader("content-type", "application/json; charset=utf-8")
	                        .setStatusCode(200)
	                        .end(jsonResult);
	                } else {
	                    System.out.println("Error: " + res.cause().getLocalizedMessage());
	                    routingContext.response().setStatusCode(500)
	                        .end("Error al obtener los estados de sensores: " + res.cause().getMessage());
	                }
	                // Cerramos la conexión después de obtener los resultados
	                connection.result().close();
	            });
	        } else {
	            System.out.println(connection.cause().toString());
	            routingContext.response().setStatusCode(500)
	                .end("Error con la conexión: " + connection.cause().getMessage());
	        }
	    });
	}
	//SensorAc
    
    private void getSensorAcById(RoutingContext routingContext) {
        mySqlClient.getConnection(connection -> {
            int idSensorAC = Integer.parseInt(routingContext.request().getParam("sensorAC")); // Accedemos al parámetro "id" en lugar de "sensor"
            if (connection.succeeded()) {
                connection.result().preparedQuery("SELECT * FROM wait.sensorsac WHERE idSensorsAC = ?;", Tuple.of(idSensorAC), res -> {
                    if (res.succeeded()) {
                        // Get the result set
                        RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<SensorAC> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new SensorAC(elem.getInteger("idDevice"),
									 elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
                    // Cerramos la conexión después de obtener los resultados
                    connection.result().close();
                });
            } else {
                System.out.println(connection.cause().toString());
                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
            }
        });
    }
    
   
    
    
    protected void addSensorAc(RoutingContext routingContext) {
    	
    	 final SensorAC sensorAC = gson.fromJson(routingContext.getBodyAsString(),
		    		SensorAC.class);
    	 
    	 mySqlClient.preparedQuery(
 				"INSERT INTO wait.sensorsac (idDevice, removed) VALUES (?,?);",
 				Tuple.of(sensorAC.getIdDevice(), sensorAC.isRemoved()),
 				res -> {
 					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						sensorAC.setIdSensorAC((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Sensor añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
    
    protected void deleteSensorAc(RoutingContext routingContext) {
    	
   	 int idSensorAc = Integer.parseInt(routingContext.request().getParam("sensorAcid"));
		
		mySqlClient.preparedQuery("DELETE FROM wait.sensorsac WHERE idSensorsAC = ?;", Tuple.of(idSensorAc), res -> {
			if (res.succeeded()) {
				 if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Sensor eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
    
    //SensorACStates
    
    private void getSensorAcStateById(RoutingContext routingContext) {
        mySqlClient.getConnection(connection -> {
            int idSensorACStates = Integer.parseInt(routingContext.request().getParam("sensorAcState")); // Accedemos al parámetro "id" en lugar de "sensor"
            if (connection.succeeded()) {
                connection.result().preparedQuery("SELECT * FROM wait.sensorsacstates WHERE idSensorsACStates = ?;", Tuple.of(idSensorACStates), res -> {
                    if (res.succeeded()) {
                        // Get the result set
                        RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<sensorACStates> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new sensorACStates(elem.getInteger("idSensorAC"), elem.getInteger("valueAc"), elem.getInteger("valueGir"),
									 elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
                    // Cerramos la conexión después de obtener los resultados
                    connection.result().close();
                });
            } else {
                System.out.println(connection.cause().toString());
                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
            }
        });
    }
    
    protected void addSensorAcState(RoutingContext routingContext) {
    	
   	 final sensorACStates sensorACStates = gson.fromJson(routingContext.getBodyAsString(),
		    		sensorACStates.class);
   	 
   	 mySqlClient.preparedQuery(
				"INSERT INTO wait.sensorsacstates (idSensorAC, valueAc, ValueGir, removed) VALUES (?,?,?,?);",
				Tuple.of(sensorACStates.getIdSensorAC(), sensorACStates.getValueAc(), sensorACStates.getValueGir(), sensorACStates.isRemoved()),
				res -> {
					if (res.succeeded()) {
						//String topic =  sensorAC.getIdDevice().toString();
	                    //String payload = gson.toJson(sensorAC);
	                    //mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						sensorACStates.setIdSensorAC((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                               "application/json; charset=utf-8").end("Sensor añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                       routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
    
    protected void deleteSensorAcState(RoutingContext routingContext) {
    	
   	 int idSensorAcStates = Integer.parseInt(routingContext.request().getParam("sensorAcStatesid"));
		
		mySqlClient.preparedQuery("DELETE FROM wait.sensorsacstates WHERE idSensorsACStates = ?;", Tuple.of(idSensorAcStates), res -> {
			if (res.succeeded()) {
				 if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Sensor eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
    
    //SensorGps
    
    private void getSensorGpsById(RoutingContext routingContext) {
        mySqlClient.getConnection(connection -> {
            int idSensorGps = Integer.parseInt(routingContext.request().getParam("sensorGps")); // Accedemos al parámetro "id" en lugar de "sensor"
            if (connection.succeeded()) {
                connection.result().preparedQuery("SELECT * FROM wait.sensorsgps WHERE idSensorsGps = ?;", Tuple.of(idSensorGps), res -> {
                    if (res.succeeded()) {
                        // Get the result set
                        RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<SensorGps> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new SensorGps(elem.getInteger("idDevice"),
									 elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
                    // Cerramos la conexión después de obtener los resultados
                    connection.result().close();
                });
            } else {
                System.out.println(connection.cause().toString());
                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
            }
        });
    }
    
    protected void addSensorGps(RoutingContext routingContext) {
    	
   	 final SensorGps sensorGps = gson.fromJson(routingContext.getBodyAsString(),
   			SensorGps.class);
   	 
   	 mySqlClient.preparedQuery(
				"INSERT INTO wait.sensorsgps (idDevice, removed) VALUES (?,?);",
				Tuple.of(sensorGps.getIdDevice(), sensorGps.isRemoved()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						sensorGps.setIdSensorGps((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                               "application/json; charset=utf-8").end("Sensor añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                       routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
    
 
    
    
    
    
    protected void deleteSensorGps(RoutingContext routingContext) {
    	
   	 int idSensorGps = Integer.parseInt(routingContext.request().getParam("sensorGpsid"));
		
		mySqlClient.preparedQuery("DELETE FROM wait.sensorsgps WHERE idSensorsGps = ?;", Tuple.of(idSensorGps), res -> {
			if (res.succeeded()) {
				 if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Sensor eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
    
    //SensorGpsStates
    
    private void getSensorGpsStatesById(RoutingContext routingContext) {
        mySqlClient.getConnection(connection -> {
            int idSensorGpsStates = Integer.parseInt(routingContext.request().getParam("sensorGpsStates")); // Accedemos al parámetro "id" en lugar de "sensor"
            if (connection.succeeded()) {
                connection.result().preparedQuery("SELECT * FROM wait.sensorsgpsstates WHERE idSensorsGpsStates = ?;", Tuple.of(idSensorGpsStates), res -> {
                    if (res.succeeded()) {
                        // Get the result set
                        RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<sensorGpsStates> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new sensorGpsStates(elem.getInteger("idsensorsGps"), Timestamp.valueOf(elem.getLocalDateTime("fechaHora")), elem.getFloat("valueLong"), elem.getFloat("valueLat"),
									 elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
                    // Cerramos la conexión después de obtener los resultados
                    connection.result().close();
                });
            } else {
                System.out.println(connection.cause().toString());
                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
            }
        });
    }
    
    protected void addSensorGpsStates(RoutingContext routingContext) {
    	
      	 final sensorGpsStates sensorGpsStates = gson.fromJson(routingContext.getBodyAsString(),
      			sensorGpsStates.class);
      	 
      	 mySqlClient.preparedQuery(
   				"INSERT INTO wait.sensorsgpsstates (idsensorsGps, fechaHora, valueLong, valueLat, removed) VALUES (?,?,?,?,?);",
   				Tuple.of(sensorGpsStates.getIdSensorGps(), sensorGpsStates.getFechaHora(), sensorGpsStates.getValueLong(), sensorGpsStates.getValueLat(), sensorGpsStates.isRemoved()),
   				res -> {
   					if (res.succeeded()) {
   						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
   						sensorGpsStates.setIdSensorGps((int) lastInsertId);
   						routingContext.response().setStatusCode(201).putHeader("content-type",
                                  "application/json; charset=utf-8").end("Sensor añadido correctamente");
   					} else {
   						System.out.println("Error: " + res.cause().getLocalizedMessage());
                          routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
   					}
   				});
   	}
    
    
    protected void deleteSensorGpsStates(RoutingContext routingContext) {
    	
      	 int idsensorGpsStates = Integer.parseInt(routingContext.request().getParam("sensorGpsStatesid"));
   		
   		mySqlClient.preparedQuery("DELETE FROM wait.sensorsgpsstates WHERE idSensorsGpsStates = ?;", Tuple.of(idsensorGpsStates), res -> {
   			if (res.succeeded()) {
   				 if (res.result().rowCount() > 0) {
                       routingContext.response()
                           .setStatusCode(200)
                           .putHeader("content-type", "application/json; charset=utf-8")
                           .end(gson.toJson(new JsonObject().put("message", "Sensor eliminado correctamente")));
                   } 
   			} else {
   				System.out.println("Error: " + res.cause().getLocalizedMessage());
   	            routingContext.response()
   	                    .setStatusCode(500)
   	                    .end("Error al conectar con la base de datos: ");
   			}
   		});
   	}
   
	
	//Devices
	
 
    
	
	protected void getDeviceById(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("device"));
		mySqlClient.preparedQuery("SELECT * FROM wait.devices WHERE idDevice = ?;", Tuple.of(deviceId), res -> {
			 if (res.succeeded()) {
                 // Get the result set
                 RowSet<Row> resultSet = res.result();
                 System.out.println(resultSet.size());
                 List<Device> result = new ArrayList<>();
                 for (Row elem : resultSet) {
                 	result.add(new Device(elem.getInteger("idDevice"), elem.getString("deviceSerialId"),
							elem.getString("name"),
							elem.getLong("lastTimestampSensorModified"), elem.getLong("lastTimestampActuatorModified")));
                 }
                 routingContext.response()
                         .putHeader("content-type", "application/json; charset=utf-8")
                         .setStatusCode(200)
                         .end(gson.toJson(result));
             } else {
                 System.out.println("Error: " + res.cause().getLocalizedMessage());
                 routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
             }
		});
	}
	
	
	
	
	

	private String extractUserIdFromToken(String authHeader) {
	    System.out.println("Authorization header recibido: " + authHeader);
	    
	    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
	        System.out.println("ERROR: Header Authorization inválido");
	        return null;
	    }
	    
	    String token = authHeader.substring(7);
	    System.out.println("Token extraído: " + token.substring(0, Math.min(20, token.length())) + "...");
	    
	    try {
	        return extractUserIdFromCustomToken(token);
	        
	    } catch (Exception e) {
	        System.out.println("Error validando token: " + e.getMessage());
	        return null;
	    }
	}
	
	protected void addDeviceUser(RoutingContext routingContext) {
	    try {
	        // Parsear el JSON del body
	        JsonObject requestBody = routingContext.getBodyAsJson();
	        
	        // Obtener datos del dispositivo
	        String deviceSerialId = requestBody.getString("deviceSerialId");
	        String deviceName = requestBody.getString("name");
	        String nickname = requestBody.getString("nickname", deviceName);
	        
	        
	        String authHeader = routingContext.request().getHeader("Authorization");
	        String userId = extractUserIdFromToken(authHeader);
	        
	        if (userId == null) {
	            System.out.println("ERROR: No se pudo extraer user_id del token");
	            routingContext.response()
	                .setStatusCode(401)
	                .end("Token de autorización inválido o faltante");
	            return;
	        }
	        
	        
	        String checkUserSQL = "SELECT COUNT(*) as count FROM users WHERE user_id = ?";
	        
	        mySqlClient.preparedQuery(checkUserSQL, Tuple.of(userId), userCheck -> {
	            if (userCheck.succeeded()) {
	                Row userRow = userCheck.result().iterator().next();
	                int userCount = userRow.getInteger("count");
	                
	                if (userCount == 0) {
	                    System.out.println("ERROR: Usuario " + userId + " no existe en la base de datos");
	                    routingContext.response()
	                        .setStatusCode(400)
	                        .end("Error: Usuario no existe. ID: " + userId);
	                    return;
	                }
	                
	                
	                insertDeviceForUser(routingContext, deviceSerialId, deviceName, nickname, userId);
	                
	            } else {
	                System.out.println("Error verificando usuario: " + userCheck.cause().getMessage());
	                routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error verificando usuario");
	            }
	        });
	        
	    } catch (Exception e) {
	        System.out.println("Error procesando request: " + e.getMessage());
	        routingContext.response()
	            .setStatusCode(400)
	            .end("Error en el formato de los datos");
	    }
	}
	
	private void insertDeviceForUser(RoutingContext routingContext, String deviceSerialId, 
            String deviceName, String nickname, String userId) {

		mySqlClient.getConnection(connection -> {
		if (connection.succeeded()) {
		
		String insertDeviceSQL = "INSERT INTO devices (deviceSerialId, name, lastTimestampSensorModified, lastTimestampActuatorModified) VALUES (?, ?, ?, ?)";
		long currentTime = System.currentTimeMillis();
		
		connection.result().preparedQuery(insertDeviceSQL,
		Tuple.of(deviceSerialId, deviceName, currentTime, currentTime),
		deviceResult -> {
		if (deviceResult.succeeded()) {
		    
		    // Obtener ID del dispositivo recién creado
		    long deviceId = deviceResult.result().property(MySQLClient.LAST_INSERTED_ID);
		    
		    //Asociar dispositivo al usuario en devices_users
		    String associateSQL = "INSERT INTO devices_users (user_id, idDevice, nickname, permission_level) VALUES (?, ?, ?, ?)";
		    
		    connection.result().preparedQuery(associateSQL,
		        Tuple.of(userId, (int)deviceId, nickname, 1),
		        associateResult -> {
		            if (associateResult.succeeded()) {
		                
		                
		                JsonObject response = new JsonObject()
		                    .put("success", true)
		                    .put("message", "Dispositivo añadido correctamente")
		                    .put("device_id", (int)deviceId)
		                    .put("user_id", userId)
		                    .put("nickname", nickname);
		                
		                routingContext.response()
		                    .setStatusCode(201)
		                    .putHeader("content-type", "application/json")
		                    .end(response.encode());
		                
		                
		                connection.result().close();
		                    
		            } else {
		                System.out.println("Error al asociar: " + associateResult.cause().getMessage());
		                routingContext.response()
		                    .setStatusCode(500)
		                    .end("Error al asociar dispositivo al usuario: " + associateResult.cause().getMessage());
		                connection.result().close();
		            }
		        });
		        
		} else {
		    System.out.println("Error al crear dispositivo: " + deviceResult.cause().getMessage());
		    routingContext.response()
		        .setStatusCode(500)
		        .end("Error al crear dispositivo");
		    connection.result().close();
		}
		});
		
		} else {
			System.out.println("Error de conexión: " + connection.cause().getMessage());
			routingContext.response()
			.setStatusCode(500)
			.end("Error de conexión a la base de datos");
		}
		});
}
	
	protected void addDevice(RoutingContext routingContext) {
		final Device device = gson.fromJson(routingContext.getBodyAsString(), Device.class);
		mySqlClient.preparedQuery(
				"INSERT INTO wait.devices (deviceSerialId, name,  lastTimestampSensorModified,"
						+ " lastTimestampActuatorModified) VALUES (?,?,?,?);",
				Tuple.of(device.getDeviceSerialId(), device.getName(),
						device.getLastTimestampSensorModified(), device.getLastTimestampActuatorModified()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						device.setIdDevice((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Actuador añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("DELETE FROM wait.devices WHERE idDevice = ?;", Tuple.of(deviceId), res -> {
			if (res.succeeded()) {
				if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Dispositivo eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
	
	protected void putDevice(RoutingContext routingContext) {
		final Device device = gson.fromJson(routingContext.getBodyAsString(), Device.class);
		mySqlClient.preparedQuery(
				"UPDATE wait.devices g SET deviceSerialId = COALESCE(?, g.deviceSerialId), name = COALESCE(?, g.name), lastTimestampSensorModified = COALESCE(?, g.lastTimestampSensorModified), lastTimestampActuatorModified = COALESCE(?, g.lastTimestampActuatorModified) WHERE idDevice = ?;",
				Tuple.of(device.getDeviceSerialId(), device.getName(),
						device.getLastTimestampSensorModified(), device.getLastTimestampActuatorModified(),
						device.getIdDevice()),
				res -> {
					if (res.succeeded()) {
                        routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(gson.toJson(device));
                    } else {
					System.out.println("Error: " + res.cause().getLocalizedMessage());
	  	              routingContext.response()
	  	                .putHeader("content-type", "application/json; charset=utf-8")
	  	                        .setStatusCode(404)
	  	                        .end("Error al actualizar los sensores: " + res.cause().getMessage());
				}
				});
	}


	
	protected void getSensorsAcStatesFromDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("SELECT * FROM wait.sensorsacstates WHERE idDevice = ?;", Tuple.of(deviceId),
				res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
						List<sensorACStates> result = new ArrayList<>();
						for (Row elem : resultSet) {
							result.add(new sensorACStates(elem.getInteger("idSensorACStates"), elem.getInteger("idSensorAC"), elem.getInteger("valueAc"), elem.getInteger("valueGir"), 
									
									elem.getBoolean("removed")));
						}

						routingContext.response()
		                 .putHeader("content-type", "application/json; charset=utf-8")
		                 .setStatusCode(200)
		                 .end(gson.toJson(result));
		     } else {
		         System.out.println("Error: " + res.cause().getLocalizedMessage());
		         routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
		     }
				});
	}
	
	protected void getSensorsGpsStatesFromDevice(RoutingContext routingContext) {
	    
	    String authHeader = routingContext.request().getHeader("Authorization");
	    System.out.println("Authorization header: " + authHeader);
	    
	    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
	        System.out.println("No Authorization header found or wrong format");
	        routingContext.response().setStatusCode(401).end("Unauthorized");
	        return;
	    }

	    String token = authHeader.substring(7); // Remover "Bearer "
	    System.out.println("Token received: " + token);
	    
	    try {
	        String decodedToken = new String(java.util.Base64.getDecoder().decode(token));
	        String[] tokenParts = decodedToken.split(":");
	        
	        if (tokenParts.length < 4) {
	            System.out.println("❌ Invalid token format");
	            routingContext.response().setStatusCode(401).end("Unauthorized");
	            return;
	        }
	        
	        int userId = Integer.parseInt(tokenParts[0]);
	        String username = tokenParts[1];
	        long timestamp = Long.parseLong(tokenParts[2]);
	        String hash = tokenParts[3];
	        
	        
	        long currentTime = System.currentTimeMillis();
	        long diffMinutes = (currentTime - timestamp) / (1000 * 60);
	        System.out.println("Token age: " + diffMinutes + " minutes");
	        
	        if (diffMinutes > 15) {
	            System.out.println("❌ Token expired");
	            routingContext.response().setStatusCode(401).end("Unauthorized");
	            return;
	        }
	        
	        int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
	        System.out.println("Device ID requested: " + deviceId);
	        
	        mySqlClient.getConnection(connection -> {
	            if (connection.succeeded()) {
	                
	                String permissionQuery = "SELECT COUNT(*) as count FROM devices_users WHERE user_id = ? AND idDevice = ?";
	                
	                
	                connection.result().preparedQuery(permissionQuery, Tuple.of(userId, deviceId), permissionRes -> {
	                    if (permissionRes.succeeded()) {
	                        RowSet<Row> permissionResult = permissionRes.result();
	                        Row row = permissionResult.iterator().next();
	                        int count = row.getInteger("count");
	                        
	                        
	                        
	                        if (count == 0) {
	                            
	                            routingContext.response().setStatusCode(403).end("Forbidden: No permission for this device");
	                            connection.result().close();
	                            return;
	                        }
	                        
	                        
	                        
	                        String gpsQuery = 
	                            "SELECT gps_states.idsensorsGpsStates, gps_states.idsensorsGps, gps_states.fechaHora, " +
	                            "gps_states.valueLong, gps_states.valueLat, gps_states.removed " +
	                            "FROM wait.sensorsgpsstates gps_states " +
	                            "INNER JOIN wait.sensorsgps gps_sensor ON gps_states.idsensorsGps = gps_sensor.idSensorsGps " +
	                            "WHERE gps_sensor.idDevice = ? AND gps_states.removed = 0 " +
	                            "ORDER BY gps_states.idsensorsGpsStates DESC " +
	                            "LIMIT 1";
	                        
	                        System.out.println("GPS query: " + gpsQuery + " with deviceId=" + deviceId);
	                        
	                        connection.result().preparedQuery(gpsQuery, Tuple.of(deviceId), res -> {
	                            if (res.succeeded()) {
	                                RowSet<Row> resultSet = res.result();
	                                System.out.println("✅ GPS query successful. Results found: " + resultSet.size());
	                                
	                                List<sensorGpsStates> result = new ArrayList<>();
	                                for (Row elem : resultSet) {
	                                    result.add(new sensorGpsStates(
	                                        elem.getInteger("idsensorsGps"), 
	                                        Timestamp.valueOf(elem.getLocalDateTime("fechaHora")), 
	                                        elem.getFloat("valueLong"), 
	                                        elem.getFloat("valueLat"),
	                                        elem.getBoolean("removed")
	                                    ));
	                                }                        
	        
	                                
	                                routingContext.response()
	                                    .putHeader("content-type", "application/json; charset=utf-8")
	                                    .setStatusCode(200)
	                                    .end(gson.toJson(result));
	                                    
	                            } else {
	                                System.out.println("❌ Error getting GPS data: " + res.cause().getLocalizedMessage());
	                                routingContext.response().setStatusCode(500).end("Error al obtener los datos GPS: " + res.cause().getMessage());
	                            }
	                            connection.result().close();
	                        });
	                        
	                    } else {
	                        System.out.println("❌ Error checking permissions: " + permissionRes.cause().getLocalizedMessage());
	                        routingContext.response().setStatusCode(500).end("Error verificando permisos: " + permissionRes.cause().getMessage());
	                        connection.result().close();
	                    }
	                });
	                
	            } else {
	                System.out.println("❌ Database connection failed: " + connection.cause().toString());
	                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
	            }
	        });
	        
	    } catch (Exception e) {
	        System.out.println("❌ Error processing token: " + e.getMessage());
	        e.printStackTrace();
	        routingContext.response().setStatusCode(401).end("Unauthorized");
	    }
	}
	
	
	
	protected void getActuatorsFromDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("SELECT * FROM wait.actuators WHERE idDevice = ?;", Tuple.of(deviceId),
				res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
						List<Actuator> result = new ArrayList<>();
						for (Row elem : resultSet) {
							result.add(new Actuator(elem.getInteger("idActuator"), elem.getString("name"),
									elem.getInteger("idDevice"), elem.getString("actuatorType"),
									elem.getBoolean("removed")));
						}

						routingContext.response()
		                 .putHeader("content-type", "application/json; charset=utf-8")
		                 .setStatusCode(200)
		                 .end(gson.toJson(result));
		     } else {
		         System.out.println("Error: " + res.cause().getLocalizedMessage());
		         routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
		     }
				});
	}
	

	
	
	//Actuators
	
	protected void getActuatorById(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuator"));
		mySqlClient.preparedQuery("SELECT * FROM wait.actuators WHERE idActuator = ?;", Tuple.of(actuatorId),
				res -> {
					if (res.succeeded()) {
						// Get the result set
						 RowSet<Row> resultSet = res.result();
		                 System.out.println(resultSet.size());
		                 List<Actuator> result = new ArrayList<>();
		                 for (Row elem : resultSet) {
		                 	result.add(new Actuator(elem.getInteger("idActuator"), elem.getString("name"),
									elem.getInteger("idDevice"),
									elem.getBoolean("removed")));
		                 }
						routingContext.response()
		                .putHeader("content-type", "application/json; charset=utf-8")
		                .setStatusCode(200)
		                .end(gson.toJson(result));
					} else {
						 System.out.println("Error: " + res.cause().getLocalizedMessage());
		                 routingContext.response().setStatusCode(500).end("Error al obtener los actuadores: " + res.cause().getMessage());
					}
				});
	}

	protected void addActuator(RoutingContext routingContext) {
		final Actuator actuator = gson.fromJson(routingContext.getBodyAsString(), Actuator.class);
		mySqlClient.preparedQuery(
				"INSERT INTO wait.actuators (name, idDevice, removed) VALUES (?,?,?);",
				Tuple.of(actuator.getName(), actuator.getIdDevice(),
						actuator.isRemoved()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						actuator.setIdActuator((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Actuador añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void putActuator(RoutingContext routingContext) {
		final Actuator actuator = gson.fromJson(routingContext.getBodyAsString(), Actuator.class);
		
		mySqlClient.preparedQuery(
				"UPDATE wait.actuators g SET name = COALESCE(?, g.name), idDevice = COALESCE(?, g.idDevice),  removed = COALESCE(?, g.removed) WHERE idActuator = ?;",
				Tuple.of(actuator.getName(), actuator.getIdDevice(), actuator.isRemoved(),
						actuator.getIdActuator()),
				res -> {
					if (res.succeeded()) {
						
	                        routingContext.response().setStatusCode(200).putHeader("content-type", 
	                        		"application/json; charset=utf-8").end(gson.toJson(actuator));
	                    
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
		  	              routingContext.response().putHeader("content-type", "application/json; charset=utf-8").setStatusCode(404).end("Error al actualizar los sensores: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteActuator(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuatorid"));
		mySqlClient.preparedQuery("DELETE FROM wait.actuators WHERE idActuator = ?;", Tuple.of(actuatorId),
				res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
		                    routingContext.response()
		                        .setStatusCode(200)
		                        .putHeader("content-type", "application/json; charset=utf-8")
		                        .end(gson.toJson(new JsonObject().put("message", "Actuador eliminado correctamente")));
		                } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
			            routingContext.response()
			                    .setStatusCode(500)
			                    .end("Error al conectar con la base de datos: ");
					}
				});
	}
	



	//ActuadorValues
	protected void  getLastActuatorStatus(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuatorid"));
		mySqlClient.preparedQuery(
				"SELECT * FROM wait.actuatorstates WHERE idActuator = ? ORDER BY `timestamp` DESC LIMIT 1;",
				Tuple.of(actuatorId), res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<ActuatorStatus> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new ActuatorStatus(elem.getInteger("idActuatorState"),
									elem.getFloat("status"), elem.getBoolean("statusBinary"),
									elem.getInteger("idActuator"),elem.getLong("timestamp"),
									elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los estados de los actuadores: " + res.cause().getMessage());
                    }
				});
	}
	
	protected void addActuatorStatus(RoutingContext routingContext) {
		final ActuatorStatus actuatorState = gson.fromJson(routingContext.getBodyAsString(), ActuatorStatus.class);
		mySqlClient.preparedQuery(
				"INSERT INTO wait.actuatorstates (status, statusBinary, idActuator, timestamp, removed) VALUES (?,?,?,?,?);",
				Tuple.of(actuatorState.getStatus(), actuatorState.isStatusBinary(), actuatorState.getIdActuator(),
						actuatorState.getTimestamp(), actuatorState.isRemoved()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						actuatorState.setIdActuatorState((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Estado Actuador añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el estado del actuador: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteActuatorStatus(RoutingContext routingContext) {
		int idActuatorStatus = Integer.parseInt(routingContext.request().getParam("actuatorstatusid"));
		mySqlClient.preparedQuery("DELETE FROM wait.actuatorstates WHERE idActuatorState = ?;",
				Tuple.of(idActuatorStatus), res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
		                    routingContext.response()
		                        .setStatusCode(200)
		                        .putHeader("content-type", "application/json; charset=utf-8")
		                        .end(gson.toJson(new JsonObject().put("message", "Estado del actuador eliminado correctamente")));
		                } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
			            routingContext.response()
			                    .setStatusCode(500)
			                    .end("Error al conectar con la base de datos: ");
					}
				});
	}
	
	//Usuarios 
	
	private void registerHandler(RoutingContext routingContext) {
	    JsonObject body = routingContext.getBodyAsJson();
	    String email = body.getString("email");
	    String password = body.getString("password");
	    String username = body.getString("username");
	    String phone = body.getString("phone");
	    
	    // Validar datos de entrada
	    if (email == null || password == null || username == null) {
	    	routingContext.response()
	          .setStatusCode(400)
	          .end(new JsonObject().put("error", "Datos incompletos").encode());
	      return;
	    }
	    
	    // Hash de la contraseña antes de almacenar
	    String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
	    
	    // Crear el usuario en la base de datos
	    userService.createUser(email, hashedPassword, username, phone)
	        .onSuccess(userId -> {
	        	routingContext.response()
	              .setStatusCode(201)
	              .end(new JsonObject().put("message", "Usuario creado exitosamente").encode());
	        })
	        .onFailure(err -> {
	        	routingContext.response()
	              .setStatusCode(500)
	              .end(new JsonObject().put("error", err.getMessage()).encode());
	        });
	  }
	
	protected void loginHandler(RoutingContext routingContext) {
	    try {
	        JsonObject requestBody = routingContext.getBodyAsJson();
	        String email = requestBody.getString("email");
	        String password = requestBody.getString("password");	        
	       
	        
	        // Validar credenciales contra base de datos
	        String sql = "SELECT user_id, username, email, password_hash FROM users WHERE email = ?";
	        
	        mySqlClient.preparedQuery(sql, Tuple.of(email), result -> {
	            if (result.succeeded() && result.result().iterator().hasNext()) {
	            	System.out.println("🔍 EJECUTANDO QUERY SQL VALIDA...");
	                Row row = result.result().iterator().next();
	                
	                int userId = row.getInteger("user_id");
	                String username = row.getString("username");
	                String userEmail = row.getString("email");
	                String storedHash = row.getString("password_hash");
	                
	                System.out.println("Usuario encontrado: " + username + " (ID: " + userId + ")");
	                
	                // Verificar contraseña
	                if (verifyPassword(password, storedHash)) {
	                    
	                    String customToken = generateCustomToken(userId, username);
	                    
	                    if (customToken != null) {
	                        JsonObject response = new JsonObject()
	                            .put("token", customToken)
	                            .put("user_id", userId)
	                            .put("username", username)
	                            .put("email", userEmail)
	                            .put("message", "Login exitoso");
	                        
	                        System.out.println("Login exitoso - Token generado para user_id: " + userId);
	                        
	                        routingContext.response()
	                            .setStatusCode(200)
	                            .putHeader("content-type", "application/json")
	                            .end(response.encode());
	                    } else {
	                        System.out.println("Error generando token");
	                        routingContext.response()
	                            .setStatusCode(500)
	                            .end("{\"error\":\"Error generando token\"}");
	                    }
	                } else {
	                    // Contraseña incorrecta
	                    System.out.println("Contraseña incorrecta para: " + email);
	                    routingContext.response()
	                        .setStatusCode(401)
	                        .end("{\"error\":\"Credenciales inválidas\"}");
	                }
	            } else {
	                // Usuario no encontrado
	                System.out.println("Usuario no encontrado: " + email);
	                routingContext.response()
	                    .setStatusCode(401)
	                    .end("{\"error\":\"Credenciales inválidas\"}");
	            }
	        });
	        
	    } catch (Exception e) {
	        System.out.println("Error en login: " + e.getMessage());
	        routingContext.response()
	            .setStatusCode(400)
	            .end("{\"error\":\"Error en la petición\"}");
	    }
	}

	// Método para generar token personalizado 
	private String generateCustomToken(int userId, String username) {
	    try {
	        long timestamp = System.currentTimeMillis();
	        String secretKey = "WhereAmIaT";
	        
	        // Crear payload: user_id:username:timestamp
	        String payload = userId + ":" + username + ":" + timestamp;
	        
	        // Generar signature
	        String signature = generateSignature(payload, secretKey);
	        
	        // Token completo: payload:signature
	        String fullToken = payload + ":" + signature;
	        
	        // Codificar en Base64
	        String encodedToken = Base64.getEncoder().encodeToString(fullToken.getBytes());
	        
	        return encodedToken;
	        
	    } catch (Exception e) {
	        System.out.println("Error generando token: " + e.getMessage());
	        return null;
	    }
	}

	// Método para generar signature
	private String generateSignature(String payload, String secretKey) {
	    try {
	        String combined = payload + secretKey;
	        MessageDigest md = MessageDigest.getInstance("SHA-256");
	        byte[] hashBytes = md.digest(combined.getBytes());
	        
	        StringBuilder hexString = new StringBuilder();
	        for (byte b : hashBytes) {
	            String hex = Integer.toHexString(0xff & b);
	            if (hex.length() == 1) hexString.append('0');
	            hexString.append(hex);
	        }
	        
	        return hexString.toString().substring(0, 16);
	    } catch (Exception e) {
	        return "invalid";
	    }
	}

	// Método de verificación de contraseña 
	private boolean verifyPassword(String password, String hash) {
	    try {
	        
	        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
	            return BCrypt.checkpw(password, hash);
	            
	        } else {
	            return password.equals(hash);
	        }
	    } catch (Exception e) {
	        System.out.println("Error verificando contraseña: " + e.getMessage());
	        return false;
	    }
	}
	

	private String extractUserIdFromCustomToken(String token) {
	    try {
	        byte[] decodedBytes = Base64.getDecoder().decode(token);
	        String decodedToken = new String(decodedBytes);
	        
	        System.out.println("Token decodificado: " + decodedToken);
	        
	        // Formato: user_id:username:timestamp:signature
	        String[] parts = decodedToken.split(":");
	        if (parts.length != 4) {
	            System.out.println("ERROR: Formato de token inválido");
	            return null;
	        }
	        
	        String userIdStr = parts[0];
	        String username = parts[1];
	        String timestampStr = parts[2];
	        String signature = parts[3];
	        
	        // Validar signature
	        String payload = userIdStr + ":" + username + ":" + timestampStr;
	        String expectedSignature = generateSignature(payload, "tu-clave-secreta-wait-app-2024");
	        
	        if (!signature.equals(expectedSignature)) {
	            System.out.println("ERROR: Signature inválida");
	            return null;
	        }
	        
	        // Validar expiración (24 horas)
	        long timestamp = Long.parseLong(timestampStr);
	        long currentTime = System.currentTimeMillis();
	        if (currentTime - timestamp > 24 * 60 * 60 * 1000) {
	            System.out.println("ERROR: Token expirado");
	            return null;
	        }
	        
	        
	        return userIdStr; // RETORNAR STRING, no Integer
	        
	    } catch (Exception e) {
	        
	        return null;
	    }
	}

	
	


	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		super.stop(stopFuture);
	}

}
