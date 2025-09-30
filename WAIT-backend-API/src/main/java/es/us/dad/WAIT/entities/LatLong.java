package es.us.dad.WAIT.entities;

public class LatLong {
	
	private Float latitude;
    private Float longitude;

    public LatLong(Float latitude, Float longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters y Setters
    public Float getLatitude() {
        return latitude;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public Float getLongitude() {
        return longitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

}
