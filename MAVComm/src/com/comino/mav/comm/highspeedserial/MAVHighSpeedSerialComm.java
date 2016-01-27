package com.comino.mav.comm.highspeedserial;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.lquac.msg_heartbeat;

import com.comino.mav.comm.IMAVComm;
import com.comino.mav.mavlink.IMAVLinkMsgListener;
import com.comino.mav.mavlink.MAVLinkToModelParser;
import com.comino.msp.model.DataModel;
import com.comino.msp.model.collector.ModelCollectorService;
import com.comino.msp.model.segment.Message;
import com.comino.msp.model.segment.Status;


public class MAVHighSpeedSerialComm implements IMAVComm {



	private SerialAMA0 			serialPort;
	private String	        port;

	private DataModel 			model = null;

	private MAVLinkToModelParser parser = null;

	private static IMAVComm com = null;

	public static IMAVComm getInstance(DataModel model) {
		if(com==null) 
			com = new MAVHighSpeedSerialComm(model);
		return com;
	}

	private MAVHighSpeedSerialComm(DataModel model) {
		this.model = model; int i=0;

		serialPort = new SerialAMA0();
		parser = new MAVLinkToModelParser(model,new HighSpeedSerialPortChannel(serialPort), this);
		parser.start();

	}

	/* (non-Javadoc)
	 * @see com.comino.px4.control.serial.IPX4Comm#open()
	 */
	@Override
	public boolean open() {
		serialPort.open();
		System.out.println("HighSpeedSerialPort connected");
		model.sys.setStatus(Status.MSP_CONNECTED, true);
		return true;
	}



	/* (non-Javadoc)
	 * @see com.comino.px4.control.serial.IPX4Comm#getModel()
	 */
	@Override
	public DataModel getModel() {
		return model;
	}


	/* (non-Javadoc)
	 * @see com.comino.px4.control.serial.IPX4Comm#getMessageList()
	 */
	@Override
	public List<Message> getMessageList() {
		return parser.getMessageList();
	}
	
	
	@Override
	public Map<Class<?>,MAVLinkMessage> getMavLinkMessageMap() {
		return parser.getMavLinkMessageMap();
	}

	/* (non-Javadoc)
	 * @see com.comino.px4.control.serial.IPX4Comm#close()
	 */
	@Override
	public void close() {
			serialPort.close();
		
	}


	/* (non-Javadoc)
	 * @see com.comino.px4.control.serial.IPX4Comm#write(org.mavlink.messages.MAVLinkMessage)
	 */
	@Override
	public void write(MAVLinkMessage msg) throws IOException {
			serialPort.writeBytes(msg.encode());
		
	}
	
	@Override
	public void registerProxyListener(IMAVLinkMsgListener listener) {
		parser.registerProxyListener(listener);
		
	}


	public static void main(String[] args) {
		IMAVComm comm = new MAVHighSpeedSerialComm(new DataModel());
		comm.open();


		long time = System.currentTimeMillis();


		try {


			ModelCollectorService colService = new ModelCollectorService(comm.getModel());
			colService.start();


		//	while(System.currentTimeMillis()< (time+30000)) {
			
			while(true) {
				
				
//				msg_command_long cmd = new msg_command_long(255,1);
//				cmd.target_system = 1;
//				cmd.target_component = 1;
//				cmd.command = MAV_CMD.MAV_CMD_DO_SET_MODE;
//				cmd.confirmation = 0;
//				
//				cmd.param1 = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
//				cmd.param2 = 2;
//						
//				
//				try {
//					comm.write(cmd);
//					System.out.println("Execute: "+cmd.toString());
//				} catch (IOException e1) {
//				    System.err.println(e1.getMessage());
//				}	

				msg_heartbeat msg = 	(msg_heartbeat) comm.getMavLinkMessageMap().get(msg_heartbeat.class);
				
//				if(msg!=null)
//					System.out.println(msg.toString());
//			      System.out.println(msg.custom_mode);
//				//		comm.getModel().state.print("NED:");
				//	System.out.println("REM="+comm.model.battery.p+" VOLT="+comm.model.battery.b0+" CURRENT="+comm.model.battery.c0);
				//   System.out.println("ANGLEX="+comm.model.attitude.aX+" ANGLEY="+comm.model.attitude.aY+" "+comm.model.sys.toString());
				Thread.sleep(50);
			}

//			colService.stop();
//			comm.close();
//
//			System.out.println(colService.getModelList().size()+" models collected");


			//			for(int i=0;i<colService.getModelList().size();i++) {
			//				DataModel m = colService.getModelList().get(i);
			//				System.out.println(m.attitude.aX);
			//			}


		} catch (Exception e) {
			comm.close();
			// TODO Auto-generated catch block
			e.printStackTrace();

		}




	}



	


}