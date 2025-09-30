-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Server version:               5.7.17-log - MySQL Community Server (GPL)
-- Server OS:                    Win64
-- HeidiSQL Version:             12.10.0.7000
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Dumping database structure for wait
CREATE DATABASE IF NOT EXISTS `wait` /*!40100 DEFAULT CHARACTER SET utf8 */;
USE `wait`;

-- Dumping structure for table wait.actuators
CREATE TABLE IF NOT EXISTS `actuators` (
  `idActuator` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(45) DEFAULT NULL,
  `idDevice` int(11) NOT NULL,
  `removed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idActuator`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.actuatorstates
CREATE TABLE IF NOT EXISTS `actuatorstates` (
  `idActuatorState` int(11) NOT NULL AUTO_INCREMENT,
  `status` float DEFAULT NULL,
  `idActuator` int(11) NOT NULL,
  `timestamp` bigint(20) NOT NULL,
  `removed` tinyint(1) NOT NULL DEFAULT '0',
  `statusBinary` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idActuatorState`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.devices
CREATE TABLE IF NOT EXISTS `devices` (
  `idDevice` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(45) DEFAULT NULL,
  `lastTimestampSensorModified` bigint(20) DEFAULT NULL,
  `lastTimestampActuatorModified` bigint(20) DEFAULT NULL,
  `deviceSerialId` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`idDevice`)
) ENGINE=InnoDB AUTO_INCREMENT=394 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.devices_users
CREATE TABLE IF NOT EXISTS `devices_users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `idDevice` int(11) NOT NULL,
  `permission_level` int(11) DEFAULT '1',
  `nickname` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_user_device` (`user_id`,`idDevice`),
  KEY `idDevice` (`idDevice`),
  CONSTRAINT `devices_users_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `devices_users_ibfk_2` FOREIGN KEY (`idDevice`) REFERENCES `devices` (`idDevice`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8;

-- Data exporting was unselected.

-- Dumping structure for table wait.sensorsac
CREATE TABLE IF NOT EXISTS `sensorsac` (
  `idSensorsAC` int(11) NOT NULL AUTO_INCREMENT,
  `idDevice` int(11) NOT NULL,
  `removed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idSensorsAC`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.sensorsacstates
CREATE TABLE IF NOT EXISTS `sensorsacstates` (
  `idsensorsACStates` int(11) NOT NULL AUTO_INCREMENT,
  `idSensorAC` int(11) NOT NULL,
  `valueAc` int(11) NOT NULL,
  `valueGir` int(11) NOT NULL,
  `removed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idsensorsACStates`)
) ENGINE=InnoDB AUTO_INCREMENT=1383 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.sensorsgps
CREATE TABLE IF NOT EXISTS `sensorsgps` (
  `idSensorsGps` int(11) NOT NULL AUTO_INCREMENT,
  `idDevice` int(11) NOT NULL,
  `removed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idSensorsGps`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.sensorsgpsstates
CREATE TABLE IF NOT EXISTS `sensorsgpsstates` (
  `idsensorsGpsStates` int(11) NOT NULL AUTO_INCREMENT,
  `idsensorsGps` int(11) NOT NULL,
  `fechaHora` datetime NOT NULL,
  `valueLong` float NOT NULL,
  `valueLat` float NOT NULL,
  `removed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`idsensorsGpsStates`)
) ENGINE=InnoDB AUTO_INCREMENT=1372 DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table wait.users
CREATE TABLE IF NOT EXISTS `users` (
  `user_id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(100) NOT NULL,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8;

-- Data exporting was unselected.

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
