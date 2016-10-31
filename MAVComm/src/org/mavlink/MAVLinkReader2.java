/**
 * $Id: MAVLinkReader.java 20 2013-10-11 13:42:37Z ghelle31@gmail.com $
 * $Date: 2013-10-11 15:42:37 +0200 (ven., 11 oct. 2013) $
 *
 * ======================================================
 * Copyright (C) 2012 Guillaume Helle.
 * Project : MAVLINK Java
 * Module : org.mavlink.library
 * File : org.mavlink.messages.MAVLinkReader.java
 * Author : Guillaume Helle
 *
 * ======================================================
 * HISTORY
 * Who       yyyy/mm/dd   Action
 * --------  ----------   ------
 * ghelle   24 aout 2012  Create
 * ghelle   02/04/13      Add modifications from Kevin Hester for Andropilot project
 *
 * ====================================================================
 * Licence: MAVLink LGPL
 * ====================================================================
 */

package org.mavlink;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Vector;

import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAVLinkMessageFactory;

/**
 * @author ghelle
 * @version $Rev: 20 $
 */
public class MAVLinkReader2 {


	private static final byte MAVLINK_IFLAG_SIGNED = 0x01;

	private static int MAVLINK_SIGNATURE_BLOCK_LEN = 13;
	private static int MAVLINK_HEADER_LEN = 9;


	enum t_parser_state  {
		MAVLINK_PARSE_STATE_IDLE,
		MAVLINK_PARSE_STATE_GOT_STX,
		MAVLINK_PARSE_STATE_GOT_LENGTH,
		MAVLINK_PARSE_STATE_GOT_INCOMPAT_FLAGS,
		MAVLINK_PARSE_STATE_GOT_COMPAT_FLAGS,
		MAVLINK_PARSE_STATE_GOT_SEQ,
		MAVLINK_PARSE_STATE_GOT_SYSID,
		MAVLINK_PARSE_STATE_GOT_COMPID,
		MAVLINK_PARSE_STATE_GOT_MSGID1,
		MAVLINK_PARSE_STATE_GOT_MSGID2,
		MAVLINK_PARSE_STATE_GOT_MSGID3,
		MAVLINK_PARSE_STATE_GOT_PAYLOAD,
		MAVLINK_PARSE_STATE_GOT_CRC1,
		MAVLINK_PARSE_STATE_GOT_BAD_CRC1,
		MAVLINK_PARSE_STATE_SIGNATURE_WAIT
	};

	enum mavlink_framing_t {
		MAVLINK_FRAMING_INCOMPLETE,
		MAVLINK_FRAMING_OK,
		MAVLINK_FRAMING_BAD_CRC,
		MAVLINK_FRAMING_BAD_SIGNATURE,
		MAVLINK_FRAMING_BAD_SEQUENCE,
	};

	private t_parser_state state = t_parser_state.MAVLINK_PARSE_STATE_IDLE;

	private RxMsg rxmsg = new RxMsg();

	/**
	 * Input stream
	 */
	private DataInputStream dis = null;

	public final static int RECEIVED_OFFSET = 10;

	/**
	 * Last sequence number received
	 */
	private final int[] lastPacket = new int[256];

	private final byte[] bytes = new byte[2048];

	private int offset = 0;

	private int id = 0;

	/**
	 * MAVLink messages received
	 */
	private final Vector<MAVLinkMessage> packets = new Vector<MAVLinkMessage>();

	private int lengthToRead = 0;



	/**
	 * Constructor for byte array read methods.
	 *
	 * @param start
	 *            Start byte for MAVLink version
	 */
	public MAVLinkReader2(int id) {
		this.id = id;
		this.dis = null;
		for (int i = 0; i < lastPacket.length; i++) {
			lastPacket[i] = -1;
		}
		System.out.println("MAVLinkReader2 "+id+" started");
	}

	/**
	 * @return the number of unread messages
	 */
	public int nbUnreadMessages() {
		return packets.size();
	}


	/**
	 * @return the protocol start tag
	 */
	public int getProtocol() {
		return rxmsg.start;
	}

	/**
	 * Return next message. Use it without stream in input.
	 *
	 * @param buffer
	 *            Contains bytes to build next message
	 * @param len
	 *            Number of byte to use in buffer
	 * @return MAVLink message or null
	 * @throws IOException
	 */

//	public MAVLinkMessage getNextMessage(byte[] buffer, int len) throws IOException {
//		MAVLinkMessage msg = null;
//
//		if (len > 0) {
//			for(int i=0;i<len;i++) {
//				readMavLinkMessageFromBuffer(buffer[i] & 0x00FF);
//			}
//		}
//
//		if (!packets.isEmpty()) {
//			msg = (MAVLinkMessage) packets.firstElement();
//			packets.removeElementAt(0);
//		}
//		return msg;
//	}

		  public MAVLinkMessage getNextMessage(byte[] buffer, int len) throws IOException {
		        MAVLinkMessage msg = null;
		        if (packets.isEmpty() || len > 0) {
		            for (int i = offset; i < len + offset; i++) {
		                bytes[i] = buffer[i - offset];
		            }
		            dis = new DataInputStream(new ByteArrayInputStream(bytes, 0, len + offset));
		            while (dis.available() > (rxmsg.len + 10)) {
		            	readMavLinkMessageFromBuffer(dis.readByte() & 0x00FF);
		            }
		            offset = dis.available();
		            for (int j = 0; j < offset; j++) {
		                bytes[j] = dis.readByte();
		            }
		            dis.close();
		            dis = null;
		        }
		        if (!packets.isEmpty()) {
		            msg = (MAVLinkMessage) packets.firstElement();
		            packets.removeElementAt(0);
		        }
		        return msg;
		    }





	protected  boolean readMavLinkMessageFromBuffer(int c) {
		try {

			switch(state) {
			case MAVLINK_PARSE_STATE_IDLE:
				if((byte)c==IMAVLinkMessage.MAVPROT_PACKET_START_V20) {
					rxmsg.clear();
					rxmsg.start = IMAVLinkMessage.MAVPROT_PACKET_START_V20;
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_STX;
				}
				if((byte)c==IMAVLinkMessage.MAVPROT_PACKET_START_V10) {
					rxmsg.clear();
					rxmsg.start = IMAVLinkMessage.MAVPROT_PACKET_START_V10;
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_STX;
					return true;
				}
				break;
			case MAVLINK_PARSE_STATE_GOT_STX:
				rxmsg.len=c; lengthToRead=0;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				if(rxmsg.start==IMAVLinkMessage.MAVPROT_PACKET_START_V10)
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_COMPAT_FLAGS;
				else
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_LENGTH;
				break;
			case MAVLINK_PARSE_STATE_GOT_LENGTH:
				rxmsg.incompat = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_INCOMPAT_FLAGS;
				break;
			case MAVLINK_PARSE_STATE_GOT_INCOMPAT_FLAGS:
				rxmsg.compat = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_COMPAT_FLAGS;
				break;
			case MAVLINK_PARSE_STATE_GOT_COMPAT_FLAGS:
				rxmsg.packet = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_SEQ;
				break;
			case MAVLINK_PARSE_STATE_GOT_SEQ:
				rxmsg.sysId = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_SYSID;
				break;
			case MAVLINK_PARSE_STATE_GOT_SYSID:
				rxmsg.componentId = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_COMPID;
				break;
			case MAVLINK_PARSE_STATE_GOT_COMPID:
				rxmsg.msgId = c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				if(rxmsg.start==IMAVLinkMessage.MAVPROT_PACKET_START_V10)
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_MSGID3;
				else
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_MSGID1;
				break;
			case MAVLINK_PARSE_STATE_GOT_MSGID1:
				rxmsg.msgId |= c << 8;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_MSGID2;
				break;
			case MAVLINK_PARSE_STATE_GOT_MSGID2:
				rxmsg.msgId |= c << 16;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				state = t_parser_state.MAVLINK_PARSE_STATE_GOT_MSGID3;
				break;
			case MAVLINK_PARSE_STATE_GOT_MSGID3:
				rxmsg.rawData[lengthToRead++] = (byte)c;
				rxmsg.crc = MAVLinkCRC.crc_accumulate((byte)c, rxmsg.crc);
				if(lengthToRead >= rxmsg.len)
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_PAYLOAD;
				break;
			case MAVLINK_PARSE_STATE_GOT_PAYLOAD:
				try {
					if (IMAVLinkCRC.MAVLINK_EXTRA_CRC)
						rxmsg.crc = MAVLinkCRC.crc_accumulate((byte) IMAVLinkCRC.MAVLINK_MESSAGE_CRCS[rxmsg.msgId], rxmsg.crc);
				} catch(Exception e) {
					System.out.println("CRC: "+rxmsg.toString());
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_BAD_CRC1;
				}

				if(c!=(rxmsg.crc & 0x00FF))
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_BAD_CRC1;
				else
					state = t_parser_state.MAVLINK_PARSE_STATE_GOT_CRC1;
				break;
			case MAVLINK_PARSE_STATE_GOT_BAD_CRC1:
			case MAVLINK_PARSE_STATE_GOT_CRC1:
				if (state == t_parser_state.MAVLINK_PARSE_STATE_GOT_BAD_CRC1 || c != (rxmsg.crc >> 8 & 0x00FF)) {
					rxmsg.msg_received = mavlink_framing_t.MAVLINK_FRAMING_BAD_CRC;
				}
				else
					rxmsg.msg_received = mavlink_framing_t.MAVLINK_FRAMING_OK;

				if((rxmsg.incompat & MAVLINK_IFLAG_SIGNED)==1) {
					rxmsg.msg_received = mavlink_framing_t.MAVLINK_FRAMING_INCOMPLETE;
					rxmsg.signature_wait = MAVLINK_SIGNATURE_BLOCK_LEN;
					state = t_parser_state.MAVLINK_PARSE_STATE_SIGNATURE_WAIT;
				}  else {

				}
				state = t_parser_state.MAVLINK_PARSE_STATE_IDLE;
				if(rxmsg.msg_received == mavlink_framing_t.MAVLINK_FRAMING_OK) {
					MAVLinkMessage msg = MAVLinkMessageFactory.getMessage(rxmsg.msgId, rxmsg.sysId, rxmsg.componentId, rxmsg.rawData);
					if(msg!=null) {//&& checkPacket(rxmsg.sysId,rxmsg.sequence)) {
						msg.packet = rxmsg.packet;
						packets.addElement(msg);
					} else {
						System.err.println("Sequence error: "+rxmsg.packet+"\t"+msg);
					}
				}
				break;

			case MAVLINK_PARSE_STATE_SIGNATURE_WAIT:
				rxmsg.signature[MAVLINK_SIGNATURE_BLOCK_LEN-rxmsg.signature_wait] = (byte)c;
				rxmsg.signature_wait--;
				if ( rxmsg.signature_wait == 0) {
					// check signature here
					// ...
					state = t_parser_state.MAVLINK_PARSE_STATE_IDLE;
					if(rxmsg.msg_received == mavlink_framing_t.MAVLINK_FRAMING_OK) {
						MAVLinkMessage msg = MAVLinkMessageFactory.getMessage(rxmsg.msgId, rxmsg.sysId, rxmsg.componentId, rxmsg.rawData);
						if(msg!=null && checkPacket(rxmsg.sysId,rxmsg.packet)) {
							msg.packet = rxmsg.packet;
							packets.addElement(msg);
							System.out.println(rxmsg.packet+":"+msg);
						} else {
							System.err.println(rxmsg.packet);
						}
					}
				}
				break;
			default: break;
			}
			return true;
		} catch(Exception io) {
			io.printStackTrace();
			state = t_parser_state.MAVLINK_PARSE_STATE_IDLE;
			return false;
		}
	}



	/**
	 * Check if we don't lost messages...
	 *
	 * @param sequence
	 *            current sequence
	 * @return true if we don't lost messages
	 */
	protected boolean checkPacket(int sysId, int packet) {
		boolean check = false;
		if (lastPacket[sysId] == -1) {
			// it is the first message read
			lastPacket[sysId] = packet;
			check = true;
		}
		else if (lastPacket[sysId] < packet) {
			if (packet - lastPacket[sysId] == 1) {
				lastPacket[sysId] = packet;
				check = true;
			}
		}
		else
			// We have reached the max number (255) and restart to 0
			if (packet + 256 - lastPacket[sysId] == 1) {
				lastPacket[sysId] = packet;
				check = true;
			}
		return check;
	}


	private class RxMsg {
		public int start;
		public int len;
		public int incompat;
		public int compat;
		public int packet;
		public int sysId;
		public int componentId;
		public int msgId;
		public int crc = MAVLinkCRC.crc_init();;
		public byte[] rawData = new byte[256];
		public byte[] signature = new byte[MAVLINK_SIGNATURE_BLOCK_LEN];

		public mavlink_framing_t msg_received;
		public int signature_wait = MAVLINK_SIGNATURE_BLOCK_LEN;

		public void clear() {
			start = 0;
			len = 0;
			incompat=0;
			compat=0;
			packet=0;
			sysId=0;
			componentId=0;
			msgId=0;
			signature_wait = MAVLINK_SIGNATURE_BLOCK_LEN;
			crc= MAVLinkCRC.crc_init();
			Arrays.fill(rawData, (byte)0x00);
			Arrays.fill(signature, (byte)0x00);
		}

		public String toString() {
			return "STX="+start+" LEN="+len+" PAK="+packet+" SYS="+sysId+" MSG="+msgId;
		}

	}

}