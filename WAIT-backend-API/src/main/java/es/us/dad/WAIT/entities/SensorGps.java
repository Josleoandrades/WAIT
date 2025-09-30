package es.us.dad.WAIT.entities;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

public class SensorGps {

	private Integer idSensorGps;
	
	private Integer idDevice;
    
    private Boolean removed;
    
    public SensorGps() {
    	super();
    }
    
	public SensorGps(Integer idDevice, Boolean removed) {
		super();
		this.idDevice = idDevice;
		this.removed = removed;
	}

	public SensorGps(Integer idSensorGps, Integer idDevice, Boolean removed) {
		super();
		this.idSensorGps = idSensorGps;
		this.idDevice = idDevice;
		this.removed = removed;
	}

	public Integer getIdSensorGps() {
		return idSensorGps;
	}

	public void setIdSensorGps(Integer idSensorGps) {
		this.idSensorGps = idSensorGps;
	}

	public Integer getIdDevice() {
		return idDevice;
	}

	public void setIdDevice(Integer idDevice) {
		this.idDevice = idDevice;
	}


	public Boolean isRemoved() {
		return removed;
	}

	public void setRemoved(Boolean removed) {
		this.removed = removed;
	}

	@Override
	public int hashCode() {
		return Objects.hash(idDevice, idSensorGps, removed);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SensorGps other = (SensorGps) obj;
		return  Objects.equals(idDevice, other.idDevice)
				&& Objects.equals(idSensorGps, other.idSensorGps) && Objects.equals(removed, other.removed);
	}

	@Override
	public String toString() {
		return "SensorGps [idSensorGps=" + idSensorGps + ", idDevice=" + idDevice + ", fechaHora=" + ", removed=" + removed + "]";
	}
	
	
}
