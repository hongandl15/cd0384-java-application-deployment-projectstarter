package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private boolean catDetectedStatus = false;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            if (catDetectedStatus && armingStatus == ArmingStatus.ARMED_HOME) {
                setAlarmStatus(AlarmStatus.ALARM);
            } else {
                securityRepository.getSensors().forEach(s -> s.setActive(false));
            }
        }
        securityRepository.setArmingStatus(armingStatus);
    }

    private void catDetected(Boolean cat) {
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        catDetectedStatus = cat;
        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if (securityRepository.getArmingStatus() != ArmingStatus.DISARMED) {
            if (active) {
                if (getAlarmStatus() == AlarmStatus.NO_ALARM) {
                    setAlarmStatus(AlarmStatus.PENDING_ALARM);
                } else if (getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
                    setAlarmStatus(AlarmStatus.ALARM);
                }
            } else if (getAlarmStatus() == AlarmStatus.PENDING_ALARM && securityRepository.getSensors().stream().noneMatch(Sensor::getActive)) {
                setAlarmStatus(AlarmStatus.NO_ALARM);
            }
        }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
