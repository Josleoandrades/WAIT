package es.us.dad.WAIT.entities;

import java.sql.Timestamp;
import java.util.Objects;

public class sensorGpsStates {

	
	private Integer idSensorGpsStates;
	
	private Integer idSensorGps;
	
	private Timestamp fechaHora;
	
	private Float valueLong;
	
	private Float valueLat;
	
	private Boolean removed;
	
	public sensorGpsStates() {
		super();
	}
	
	public sensorGpsStates(Integer idSensorGps, Timestamp fechaHora, Float valueLong, Float valueLat, Boolean removed) {
		super();
		this.idSensorGps = idSensorGps;
		this.fechaHora = fechaHora;
		this.valueLong = valueLong;
		this.valueLat = valueLat;
		this.removed = removed;
	}

	public sensorGpsStates(Integer idSensorGpsStates, Integer idSensorGps, Timestamp fechaHora, Float valueLong, Float valueLat, Boolean removed) {
		super();
		this.idSensorGpsStates = idSensorGpsStates;
		this.idSensorGps = idSensorGps;
		this.fechaHora = fechaHora;
		this.valueLong = valueLong;
		this.valueLat = valueLat;
		this.removed = removed;
		
	}

	public Integer getIdSensorGpsStates() {
		return idSensorGpsStates;
	}

	public void setIdSensorGpsStates(Integer idSensorGpsStates) {
		this.idSensorGpsStates = idSensorGpsStates;
	}

	public Integer getIdSensorGps() {
		return idSensorGps;
	}

	public void setIdSensorGps(Integer idSensorGps) {
		this.idSensorGps = idSensorGps;
	}

	public Timestamp getFechaHora() {
		return fechaHora;
	}

	public void setFechaHora(Timestamp fechaHora) {
		this.fechaHora = fechaHora;
	}

	public Float getValueLong() {
		return valueLong;
	}

	public void setValueLong(Float valueLong) {
		this.valueLong = valueLong;
	}

	public Float getValueLat() {
		return valueLat;
	}

	public void setValueLat(Float valueLat) {
		this.valueLat = valueLat;
	}

	public Boolean isRemoved() {
		return removed;
	}

	public void setRemoved(Boolean removed) {
		this.removed = removed;
	}


	@Override
	public int hashCode() {
		return Objects.hash(fechaHora, idSensorGps, idSensorGpsStates, removed, valueLat, valueLong);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		sensorGpsStates other = (sensorGpsStates) obj;
		return Objects.equals(fechaHora, other.fechaHora) && Objects.equals(idSensorGps, other.idSensorGps)
				&& Objects.equals(idSensorGpsStates, other.idSensorGpsStates) && Objects.equals(removed, other.removed)
				&& Objects.equals(valueLat, other.valueLat) && Objects.equals(valueLong, other.valueLong);
	}

	@Override
	public String toString() {
		return "sensorGpsStares [idSensorGpsStates=" + idSensorGpsStates + ", idSensorGps=" + idSensorGps
				+ ", fechaHora=" + fechaHora + ", valueLong=" + valueLong + ", valueLat=" + valueLat + ", removed="
				+ removed + "]";
	}
	
	
	
}
