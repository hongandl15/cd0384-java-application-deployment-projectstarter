package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @Mock
    static ImageService mockImageService;
    @Mock
    static SecurityRepository mockSecurityRepository;
    static Sensor testSensor = new Sensor("testSensor", SensorType.DOOR);
    @Mock
    static BufferedImage testBufferedImage;

    static SecurityService securitySystem;

    @BeforeEach
    public void initialize() {
        MockitoAnnotations.initMocks(this);
        securitySystem = new SecurityService(mockSecurityRepository, mockImageService);
    }

    @AfterAll
    public static void cleanUp() {
        mockImageService = null;
        mockSecurityRepository = null;
        testSensor = null;
        securitySystem = null;
        testBufferedImage = null;
    }

    @ParameterizedTest
    @MethodSource("getSensorActivationTestArguments")
    public void alarmTriggersWhenSensorActivated(ArmingStatus armingStatus, SensorType sensorType) {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(armingStatus);
        testSensor.setSensorType(sensorType);
        testSensor.setActive(false);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securitySystem.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    private static Object[][] getSensorActivationTestArguments() {
        return new Object[][] {
                {ArmingStatus.ARMED_AWAY, SensorType.MOTION},
                {ArmingStatus.ARMED_HOME, SensorType.MOTION},
                {ArmingStatus.ARMED_AWAY, SensorType.DOOR},
                {ArmingStatus.ARMED_HOME, SensorType.DOOR},
                {ArmingStatus.ARMED_AWAY, SensorType.WINDOW},
                {ArmingStatus.ARMED_HOME, SensorType.WINDOW}
        };
    }

    @ParameterizedTest
    @MethodSource("getSensorActivationTestArguments")
    public void alarmStatusUpdatesToActiveWhenSensorActivatedAndPending(ArmingStatus armingStatus, SensorType sensorType) {
        when(mockSecurityRepository.getArmingStatus()).thenReturn(armingStatus);
        testSensor.setSensorType(sensorType);
        testSensor.setActive(false);
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securitySystem.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    public void noAlarmWhenAllSensorsInactiveDuringPendingState(SensorType sensorType) {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        testSensor.setSensorType(sensorType);
        testSensor.setActive(false);
        securitySystem.changeSensorActivationStatus(testSensor, false);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @MethodSource("getTestArgumentsForInactiveSensor")
    public void alarmStatusUnchangedWhenActive(ArmingStatus armingStatus, SensorType sensorType, boolean isActive) {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        testSensor.setSensorType(sensorType);
        testSensor.setActive(isActive);
        securitySystem.changeSensorActivationStatus(testSensor, !isActive);
        verify(mockSecurityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    private static Object[][] getTestArgumentsForInactiveSensor() {
        return new Object[][] {
                {ArmingStatus.ARMED_AWAY, SensorType.MOTION, true},
                {ArmingStatus.ARMED_HOME, SensorType.MOTION, false},
                {ArmingStatus.ARMED_AWAY, SensorType.DOOR, true},
                {ArmingStatus.ARMED_HOME, SensorType.DOOR, false},
                {ArmingStatus.ARMED_AWAY, SensorType.WINDOW, true},
                {ArmingStatus.ARMED_HOME, SensorType.WINDOW, false}
        };
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    public void alarmStatusChangesWhenSensorActivatedDuringPendingState(SensorType sensorType) {
        when(mockSecurityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        testSensor.setActive(true);
        testSensor.setSensorType(sensorType);
        securitySystem.changeSensorActivationStatus(testSensor, true);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    public void noChangeInAlarmWhenSensorAlreadyInactive(SensorType sensorType) {
        testSensor.setSensorType(sensorType);
        testSensor.setActive(false);
        securitySystem.changeSensorActivationStatus(testSensor, false);
        verify(mockSecurityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    public void alarmTriggersOnCatDetectionWhileArmedHome(SensorType sensorType) {
        testSensor.setSensorType(sensorType);
        when(mockSecurityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockImageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        securitySystem.processImage(testBufferedImage);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    public void noAlarmWhenNoCatDetected() {
        when(mockImageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        securitySystem.processImage(testBufferedImage);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    public void noAlarmWhenSystemIsDisarmed() {
        securitySystem.setArmingStatus(ArmingStatus.DISARMED);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @MethodSource("getTestArgumentsForResetSensors")
    public void resetSensorsWhenArmed(Set<Sensor> sensors, ArmingStatus armingStatus) {
        when(mockSecurityRepository.getSensors()).thenReturn(sensors);
        securitySystem.setArmingStatus(armingStatus);
        securitySystem.getSensors().forEach(s -> assertFalse(s.getActive()));
    }

    private static Object[][] getTestArgumentsForResetSensors() {
        Sensor sensor1 = new Sensor("sensor1", SensorType.DOOR);
        Sensor sensor2 = new Sensor("sensor2", SensorType.DOOR);
        sensor1.setActive(true);
        sensor2.setActive(false);
        return new Object[][] {
                {Set.of(sensor1, sensor2), ArmingStatus.ARMED_AWAY},
                {Set.of(sensor1, sensor2), ArmingStatus.ARMED_HOME}
        };
    }

    @Test
    public void alarmTriggersWhenArmedHomeAndCatDetected() {
        when(mockImageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        securitySystem.processImage(testBufferedImage);
        securitySystem.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(mockSecurityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }
}
