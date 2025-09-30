package es.us.dad.WAIT.entities;

import java.util.Objects;

public class SensorAC {
	
	private Integer idSensorAC;
	
	private Integer idDevice;
	
	
	private Boolean removed;
	
	public SensorAC() {
		super();
	}
	
	public SensorAC(Integer idDevice, Boolean removed) {
		super();
		this.idDevice = idDevice;
		this.removed = removed;
	}

	public SensorAC(Integer idSensorAC, Integer idDevice, Boolean removed) {
		super();
		this.idSensorAC = idSensorAC;
		this.idDevice = idDevice;
		this.removed = removed;
	}

	public Integer getIdSensorAC() {
		return idSensorAC;
	}

	public void setIdSensorAC(Integer idSensorAC) {
		this.idSensorAC = idSensorAC;
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
		return Objects.hash(idDevice, idSensorAC, removed);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SensorAC other = (SensorAC) obj;
		return Objects.equals(idDevice, other.idDevice) && Objects.equals(idSensorAC, other.idSensorAC)
				&& Objects.equals(removed, other.removed);
	}

	@Override
	public String toString() {
		return "SensorAC [idSensorAC=" + idSensorAC + ", idDevice=" + idDevice + ", removed=" + removed + "]";
	}

	


}
