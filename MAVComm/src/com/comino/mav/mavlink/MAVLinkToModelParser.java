/*
 * Copyright (c) 2016 by E.Mansfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comino.mav.mavlink;

import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_MODE_FLAG_DECODE_POSITION;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.MAV_SYS_STATUS_SENSOR;
import org.mavlink.messages.lquac.msg_altitude;
import org.mavlink.messages.lquac.msg_attitude;
import org.mavlink.messages.lquac.msg_battery_status;
import org.mavlink.messages.lquac.msg_command_ack;
import org.mavlink.messages.lquac.msg_distance_sensor;
import org.mavlink.messages.lquac.msg_extended_sys_state;
import org.mavlink.messages.lquac.msg_global_position_int;
import org.mavlink.messages.lquac.msg_gps_raw_int;
import org.mavlink.messages.lquac.msg_heartbeat;
import org.mavlink.messages.lquac.msg_highres_imu;
import org.mavlink.messages.lquac.msg_home_position;
import org.mavlink.messages.lquac.msg_local_position_ned;
import org.mavlink.messages.lquac.msg_optical_flow_rad;
import org.mavlink.messages.lquac.msg_position_target_local_ned;
import org.mavlink.messages.lquac.msg_rc_channels;
import org.mavlink.messages.lquac.msg_servo_output_raw;
import org.mavlink.messages.lquac.msg_statustext;
import org.mavlink.messages.lquac.msg_sys_status;
import org.mavlink.messages.lquac.msg_vfr_hud;
import org.mavlink.messages.lquac.msg_vibration;

import com.comino.mav.comm.IMAVComm;
import com.comino.msp.main.control.listener.IMAVLinkListener;
import com.comino.msp.main.control.listener.IMAVMessageListener;
import com.comino.msp.main.control.listener.IMSPModeChangedListener;
import com.comino.msp.model.DataModel;
import com.comino.msp.model.segment.GPS;
import com.comino.msp.model.segment.LogMessage;
import com.comino.msp.model.segment.Status;


public class MAVLinkToModelParser {


	private MAVLinkStream stream;
	private DataModel model;

	private ArrayList<LogMessage>					msgList     = null;
	private HashMap<Class<?>,MAVLinkMessage>	    mavList     = null;

	private IMAVComm link = null;

	private HashMap<Class<?>,IMAVLinkListener> listeners = null;
	private List<IMAVLinkListener> mavListener 			 = null;
	private List<IMAVMessageListener> msgListener        = null;

	private List<IMSPModeChangedListener> modeListener = null;

	private boolean isRunning = false;
	private long    startUpAt = 0;
	private long    t_armed_start = 0;

	private Status oldStatus = new Status();

	public MAVLinkToModelParser(DataModel model, IMAVComm link) {

		this.model = model;
		this.link = link;
		this.msgList   = new ArrayList<LogMessage>();
		this.mavList   = new HashMap<Class<?>,MAVLinkMessage>();

		this.modeListener = new ArrayList<IMSPModeChangedListener>();
		this.mavListener = new ArrayList<IMAVLinkListener>();
		this.msgListener = new ArrayList<IMAVMessageListener>();

		listeners = new HashMap<Class<?>,IMAVLinkListener>();


		registerListener(msg_vfr_hud.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_vfr_hud hud   = (msg_vfr_hud)o;
				model.attitude.h  = hud.heading;
				model.attitude.s  = hud.groundspeed;
				model.attitude.vs = hud.climb;
				model.state.h     = hud.heading;
			}
		});

		registerListener(msg_distance_sensor.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_distance_sensor lidar = (msg_distance_sensor)o;
				model.raw.di = lidar.current_distance / 100f;
				model.sys.setSensor(Status.MSP_LIDAR_AVAILABILITY, true);


			}
		});

		registerListener(msg_servo_output_raw.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_servo_output_raw servo = (msg_servo_output_raw)o;
				model.servo.s0 = (short)servo.servo1_raw;
				model.servo.s1 = (short)servo.servo2_raw;
				model.servo.s2 = (short)servo.servo3_raw;
				model.servo.s3 = (short)servo.servo4_raw;
				model.servo.s4 = (short)servo.servo5_raw;
				model.servo.s5 = (short)servo.servo6_raw;
				model.servo.s6 = (short)servo.servo7_raw;
				model.servo.s7 = (short)servo.servo8_raw;

				model.servo.tms = servo.time_usec;

			}
		});

		registerListener(msg_vibration.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_vibration vib = (msg_vibration)o;
				model.vibration.vibx = vib.vibration_x;
				model.vibration.viby = vib.vibration_y;
				model.vibration.vibz = vib.vibration_z;
				model.vibration.cli0 = vib.clipping_0;
				model.vibration.cli1 = vib.clipping_1;
				model.vibration.cli2 = vib.clipping_2;
				model.vibration.tms  = vib.time_usec;
			}
		});


		registerListener(msg_optical_flow_rad.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_optical_flow_rad flow = (msg_optical_flow_rad)o;
				model.raw.fX   = flow.integrated_x;
				model.raw.fY   = flow.integrated_y;
				model.raw.fq   = flow.quality;
				model.raw.fd   = flow.distance;

				model.raw.tms  = flow.time_usec;
			}
		});

		registerListener(msg_altitude.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_altitude alt = (msg_altitude)o;
				model.attitude.al   = alt.altitude_local;
				model.attitude.ag   = alt.altitude_amsl;
				model.attitude.at   = alt.altitude_terrain;

			}
		});

		registerListener(msg_command_ack.class, new IMAVLinkListener() {

			@Override
			public void received(Object o) {
				msg_command_ack ack = (msg_command_ack)o;
				// TODO 1.0 handle acknowledgement via Listener
			}

		});

		registerListener(msg_attitude.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_attitude att = (msg_attitude)o;
				model.attitude.aX   = att.roll;
				model.attitude.aY   = att.pitch;
				model.attitude.tms  = att.time_boot_ms*1000;
				model.sys.setSensor(Status.MSP_IMU_AVAILABILITY, true);

				//System.out.println(att.toString());
			}
		});

		registerListener(msg_gps_raw_int.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_gps_raw_int gps = (msg_gps_raw_int)o;
				model.gps.numsat = (byte) gps.satellites_visible;
				model.gps.setFlag(GPS.GPS_SAT_FIX, gps.fix_type>0);
				model.gps.setFlag(GPS.GPS_SAT_VALID, true);

				// TODO 1.0: Wrong assignment in MAVLINK (ref. #4051)
				model.gps.hdop   = gps.eph/100f;
				model.gps.latitude = gps.lat/1e7d;
				model.gps.longitude = gps.lon/1e7d;
				model.sys.setSensor(Status.MSP_GPS_AVAILABILITY, model.gps.numsat>3);

			}
		});


		registerListener(msg_home_position.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_home_position ref = (msg_home_position)o;
				model.gps.ref_lat = ref.latitude/10000000f;
				model.gps.ref_lon = ref.longitude/10000000f;
				model.gps.ref_altitude = ref.altitude;
				model.gps.setFlag(GPS.GPS_REF_VALID, true);
				model.state.g_x = ref.x;
				model.state.g_y = ref.y;
				model.state.g_z = ref.z;
			}
		});

		registerListener(msg_local_position_ned.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_local_position_ned ned = (msg_local_position_ned)o;

				model.state.x = ned.x;
				model.state.y = ned.y;
				model.state.z = ned.z;

				model.state.vx = ned.vx;
				model.state.vy = ned.vy;
				model.state.vz = ned.vz;

				model.state.tms = ned.time_boot_ms*1000;

			}
		});


		registerListener(msg_position_target_local_ned.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_position_target_local_ned ned = (msg_position_target_local_ned)o;


				model.target_state.x = ned.x;
				model.target_state.y = ned.y;
				model.target_state.z = ned.z;

				model.target_state.vx = ned.vx;
				model.target_state.vy = ned.vy;
				model.target_state.vz = ned.vz;

				model.target_state.tms = ned.time_boot_ms*1000;

			}
		});

		registerListener(msg_global_position_int.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_global_position_int pos = (msg_global_position_int)o;

				model.state.lat 	= pos.lat/10000000f;
				model.state.lon	    = pos.lon/10000000f;
				model.gps.heading   = (short)(pos.hdg/1000);
				model.gps.altitude  = (short)(pos.alt/1000);
				model.gps.tms = pos.time_boot_ms * 1000;
				model.state.g_vx = pos.vx;
				model.state.g_vy = pos.vy;
				model.state.g_vz = pos.vz;

			}
		});

		registerListener(msg_highres_imu.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_highres_imu imu = (msg_highres_imu)o;
				model.imu.accx = imu.xacc;
				model.imu.accy = imu.yacc;
				model.imu.accz = imu.zacc;

				model.imu.gyrox = imu.xgyro;
				model.imu.gyroy = imu.ygyro;
				model.imu.gyroz = imu.zgyro;

				model.imu.magx = imu.xmag;
				model.imu.magy = imu.ymag;
				model.imu.magz = imu.zmag;

				model.imu.abs_pressure = imu.abs_pressure;

				model.sys.imu_temp = (int)imu.temperature;

				model.imu.tms =imu.time_usec;

			}
		});


		registerListener(msg_statustext.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_statustext msg = (msg_statustext)o;
				LogMessage m = new LogMessage();
				m.msg = new String(msg.text);
				m.tms = System.currentTimeMillis();
				m.severity = msg.severity;

				if(msgList.size()>0) {
					if((msgList.get(msgList.size()-1).tms+100) < m.tms) {
						msgList.add(m);
						if(msgListener!=null) {
							for(IMAVMessageListener msglistener : msgListener)
								msglistener.messageReceived(msgList, m);
						}
					}
				} else {
					msgList.add(m);
					if(msgListener!=null) {
						for(IMAVMessageListener msglistener : msgListener)
							msglistener.messageReceived(msgList, m);
					}
				}


			}
		});

		registerListener(msg_rc_channels.class, new IMAVLinkListener() {

			short rssi_old=0;

			@Override
			public void received(Object o) {

				msg_rc_channels rc = (msg_rc_channels)o;

				model.rc.rssi = (short)((rc.rssi+rssi_old)/2);
				rssi_old = (short)rc.rssi;

				model.sys.setStatus(Status.MSP_RC_ATTACHED, (model.rc.rssi>0));

				model.rc.s0 = rc.chan1_raw < 65534 ? (short)rc.chan1_raw : 0;
				model.rc.s1 = rc.chan2_raw < 65534 ? (short)rc.chan2_raw : 0;
				model.rc.s2 = rc.chan3_raw < 65534 ? (short)rc.chan3_raw : 0;
				model.rc.s3 = rc.chan4_raw < 65534 ? (short)rc.chan4_raw : 0;
				model.rc.tms = rc.time_boot_ms;

				notifyStatusChange();

			}
		});


		registerListener(msg_heartbeat.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_heartbeat hb = (msg_heartbeat)o;

				model.sys.setStatus(Status.MSP_ARMED,(hb.base_mode & MAV_MODE_FLAG_DECODE_POSITION.MAV_MODE_FLAG_DECODE_POSITION_SAFETY)>0);

				model.sys.setStatus(Status.MSP_ACTIVE, (hb.system_status & MAV_STATE.MAV_STATE_ACTIVE)>0);
				model.sys.setStatus(Status.MSP_READY, (hb.system_status & MAV_STATE.MAV_STATE_STANDBY)>0);
				model.sys.setStatus(Status.MSP_ARMED, (hb.base_mode & MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED)!=0);

				model.sys.setStatus(Status.MSP_MODE_STABILIZED, (hb.base_mode & MAV_MODE_FLAG.MAV_MODE_FLAG_STABILIZE_ENABLED)!=0);

				model.sys.setStatus(Status.MSP_CONNECTED, true);

				model.sys.setStatus(Status.MSP_MODE_ALTITUDE, MAV_CUST_MODE.is(hb.custom_mode,MAV_CUST_MODE.PX4_CUSTOM_MAIN_MODE_ALTCTL));
				model.sys.setStatus(Status.MSP_MODE_POSITION, MAV_CUST_MODE.is(hb.custom_mode,MAV_CUST_MODE.PX4_CUSTOM_MAIN_MODE_POSCTL));
				model.sys.setStatus(Status.MSP_MODE_LOITER, MAV_CUST_MODE.is(hb.custom_mode,MAV_CUST_MODE.PX4_CUSTOM_SUB_MODE_AUTO_LOITER));
				model.sys.setStatus(Status.MSP_MODE_MISSION, MAV_CUST_MODE.is(hb.custom_mode,MAV_CUST_MODE.PX4_CUSTOM_SUB_MODE_AUTO_MISSION));
				model.sys.setStatus(Status.MSP_MODE_OFFBOARD, MAV_CUST_MODE.is(hb.custom_mode,MAV_CUST_MODE.PX4_CUSTOM_MAIN_MODE_OFFBOARD));

				model.sys.basemode = hb.base_mode;

				model.sys.tms = System.nanoTime()/1000;

				notifyStatusChange();
			}
		});

		registerListener(msg_battery_status.class, new IMAVLinkListener() {

			@Override
			public void received(Object o) {
				msg_battery_status bat = (msg_battery_status)o;
				if(bat.current_consumed>0)
					model.battery.a0 = bat.current_consumed;
			}

		});


		registerListener(msg_sys_status.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_sys_status sys = (msg_sys_status)o;
				model.battery.p  = (short)sys.battery_remaining;
				model.battery.b0 = sys.voltage_battery / 1000f;
				model.battery.c0 = sys.current_battery / 100f;

				model.sys.error1  = sys.errors_count1;
				model.sys.load_p  = sys.load/10;
				model.sys.drops_p = sys.drop_rate_comm/10000f;

				// Sensor availability

				model.sys.setSensor(Status.MSP_PIX4FLOW_AVAILABILITY,
						(sys.onboard_control_sensors_enabled & MAV_SYS_STATUS_SENSOR.MAV_SYS_STATUS_SENSOR_OPTICAL_FLOW)>0);

				//
				//				model.sys.setSensor(Status.MSP_GPS_AVAILABILITY,
				//						(sys.onboard_control_sensors_enabled & MAV_SYS_STATUS_SENSOR.MAV_SYS_STATUS_SENSOR_GPS)>0);




			}
		});

		registerListener(msg_extended_sys_state.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_extended_sys_state sys = (msg_extended_sys_state)o;
				model.sys.setStatus(Status.MSP_LANDED,   sys.landed_state==1);
				model.sys.setStatus(Status.MSP_INAIR,    sys.landed_state==0);
				model.sys.setStatus(Status.MSP_FREEFALL, sys.landed_state==2);

				notifyStatusChange();
			}
		});

		System.out.println("MAVLink parser: "+listeners.size()+" MAVLink messagetypes registered");

	}

	public void addMAVLinkListener(IMAVLinkListener listener) {
		mavListener.add(listener);
	}

	public void addMAVMessagekListener(IMAVMessageListener listener) {
		msgListener.add(listener);
	}

	public List<LogMessage> getMessageList() {
		return msgList;
	}

	public Map<Class<?>,MAVLinkMessage> getMavLinkMessageMap() {
		return mavList;
	}

	public void start(ByteChannel channel) {
		isRunning = true;
		stream = new MAVLinkStream(channel);
		Thread s = new Thread(new MAVLinkParserWorker());
		s.start();
	}

	public boolean isConnected() {

		if(!oldStatus.isEqual(model.sys)) {

			notifyStatusChange();

			if(!model.sys.isStatus(Status.MSP_CONNECTED)) {
				model.clear();
				return false;
			}
		}
		return model.sys.isStatus(Status.MSP_CONNECTED);
	}


	public void stop() {
		isRunning = false;
	}

	public void addModeChangeListener(IMSPModeChangedListener listener) {
		modeListener.add(listener);
	}

	public void writeMessage(LogMessage m) {
		System.out.println(m.msg);
		msgList.add(m);
		if(msgListener!=null) {
			for(IMAVMessageListener msglistener : msgListener)
				msglistener.messageReceived(msgList, m);
		}
	}


	private void registerListener(Class<?> clazz, IMAVLinkListener listener) {
		listeners.put(clazz, listener);
	}

	private class MAVLinkParserWorker implements Runnable {
		MAVLinkMessage msg = null;

		public void run() {
			startUpAt = System.currentTimeMillis();
			model.sys.tms = System.nanoTime()/1000;

			while (isRunning) {
				try {

					if(stream==null) {
						Thread.yield();
						continue;
					}

					while((msg = stream.read())!=null) {

						model.sys.setStatus(Status.MSP_CONNECTED,true);
						model.sys.tms = System.nanoTime()/1000;

						mavList.put(msg.getClass(),msg);
						IMAVLinkListener listener = listeners.get(msg.getClass());
						if(listener!=null)
							listener.received(msg);

						if(mavListener!=null) {
							for(IMAVLinkListener mavlistener : mavListener)
								mavlistener.received(msg);
						}
					}

					if((System.nanoTime()/1000) > (model.sys.tms+5000000) &&
							model.sys.isStatus(Status.MSP_CONNECTED)) {

						model.sys.setStatus(Status.MSP_CONNECTED, false);
						notifyStatusChange();

						msgList.add(new LogMessage("Connection lost",2));
						System.out.println("Connection lost");
						link.close(); link.open();
						model.sys.tms = System.nanoTime()/1000;
						Thread.sleep(50);
					}

				} catch (Exception e) {

				}
			}
		}
	}


	private void notifyStatusChange() {
		if(!oldStatus.isEqual(model.sys) && (System.currentTimeMillis() - startUpAt)>2000) {
			for(IMSPModeChangedListener listener : modeListener)
				listener.update(oldStatus, model.sys);

			if(!oldStatus.isStatus(Status.MSP_ARMED) && model.sys.isStatus(Status.MSP_ARMED))
					t_armed_start = System.currentTimeMillis();

			if(oldStatus.isStatus(Status.MSP_ARMED) && !model.sys.isStatus(Status.MSP_ARMED))
				model.sys.t_armed_ms = System.currentTimeMillis() - t_armed_start;

			oldStatus.set(model.sys);
		}

	}

}
