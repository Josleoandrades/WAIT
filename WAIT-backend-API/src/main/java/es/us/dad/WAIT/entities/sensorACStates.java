package es.us.dad.WAIT.entities;

import java.util.Objects;

public class sensorACStates {
	
	private Integer idSensorACStates;
	
	private Integer idSensorAC;
	
	private Integer valueAc;
	
	private Integer valueGir;
	
	private Boolean removed;
	
	public sensorACStates() {
		super();
	}
	
	public sensorACStates(Integer idSensorAC, Integer valueAc, Integer valueGir, Boolean removed) {
		super();
		this.idSensorAC = idSensorAC;
		this.valueAc = valueAc;
		this.valueGir = valueGir;
		this.removed = removed;
	}

	public sensorACStates(Integer idSensorACStates, Integer idSensorAC, Integer valueAc, Integer valueGir, Boolean removed) {
		super();
		this.idSensorACStates = idSensorACStates;
		this.idSensorAC = idSensorAC;
		this.valueAc = valueAc;
		this.valueGir = valueGir;
		this.removed = removed;
	}

	public Integer getIdSensorACStates() {
		return idSensorACStates;
	}

	public void setIdSensorACStates(Integer idSensorACStates) {
		this.idSensorACStates = idSensorACStates;
	}

	public Integer getIdSensorAC() {
		return idSensorAC;
	}

	public void setIdSensorAC(Integer idSensorAC) {
		this.idSensorAC = idSensorAC;
	}

	public Integer getValueAc() {
		return valueAc;
	}

	public void setValueAc(Integer valueAc) {
		this.valueAc = valueAc;
	}

	public Integer getValueGir() {
		return valueGir;
	}

	public void setValueGir(Integer valueGir) {
		this.valueGir = valueGir;
	}

	public Boolean isRemoved() {
		return removed;
	}

	public void setRemoved(Boolean removed) {
		this.removed = removed;
	}

	@Override
	public int hashCode() {
		return Objects.hash(idSensorAC, idSensorACStates, removed, valueAc, valueGir);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		sensorACStates other = (sensorACStates) obj;
		return Objects.equals(idSensorAC, other.idSensorAC) && Objects.equals(idSensorACStates, other.idSensorACStates)
				&& Objects.equals(removed, other.removed) && Objects.equals(valueAc, other.valueAc)
				&& Objects.equals(valueGir, other.valueGir);
	}

	@Override
	public String toString() {
		return "sensorACStates [idSensorACStates=" + idSensorACStates + ", idSensorAC=" + idSensorAC + ", valueAc="
				+ valueAc + ", valueGir=" + valueGir + ", removed=" + removed + "]";
	}
	
}
