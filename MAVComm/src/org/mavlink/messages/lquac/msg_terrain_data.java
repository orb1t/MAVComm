/**
 * Generated class : msg_terrain_data
 * DO NOT MODIFY!
 **/
package org.mavlink.messages.lquac;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.IMAVLinkCRC;
import org.mavlink.MAVLinkCRC;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/**
 * Class msg_terrain_data
 * Terrain data sent from GCS. The lat/lon and grid_spacing must be the same as a lat/lon from a TERRAIN_REQUEST
 **/
public class msg_terrain_data extends MAVLinkMessage {
  public static final int MAVLINK_MSG_ID_TERRAIN_DATA = 134;
  private static final long serialVersionUID = MAVLINK_MSG_ID_TERRAIN_DATA;
  public msg_terrain_data() {
    this(1,1);
}
  public msg_terrain_data(int sysId, int componentId) {
    messageType = MAVLINK_MSG_ID_TERRAIN_DATA;
    this.sysId = sysId;
    this.componentId = componentId;
    length = 43;
}

  /**
   * Latitude of SW corner of first grid (degrees *10^7)
   */
  public long lat;
  /**
   * Longitude of SW corner of first grid (in degrees *10^7)
   */
  public long lon;
  /**
   * Grid spacing in meters
   */
  public int grid_spacing;
  /**
   * Terrain data in meters AMSL
   */
  public int[] data = new int[16];
  /**
   * bit within the terrain request mask
   */
  public int gridbit;
/**
 * Decode message with raw data
 */
public void decode(ByteBuffer dis) throws IOException {
  lat = (int)dis.getInt();
  lon = (int)dis.getInt();
  grid_spacing = (int)dis.getShort()&0x00FFFF;
  for (int i=0; i<16; i++) {
    data[i] = (int)dis.getShort();
  }
  gridbit = (int)dis.get()&0x00FF;
}
/**
 * Encode message with raw data and other informations
 */
public byte[] encode() throws IOException {
  byte[] buffer = new byte[8+43];
   ByteBuffer dos = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
  dos.put((byte)0xFE);
  dos.put((byte)(length & 0x00FF));
  dos.put((byte)(sequence & 0x00FF));
  dos.put((byte)(sysId & 0x00FF));
  dos.put((byte)(componentId & 0x00FF));
  dos.put((byte)(messageType & 0x00FF));
  dos.putInt((int)(lat&0x00FFFFFFFF));
  dos.putInt((int)(lon&0x00FFFFFFFF));
  dos.putShort((short)(grid_spacing&0x00FFFF));
  for (int i=0; i<16; i++) {
    dos.putShort((short)(data[i]&0x00FFFF));
  }
  dos.put((byte)(gridbit&0x00FF));
  int crc = MAVLinkCRC.crc_calculate_encode(buffer, 43);
  crc = MAVLinkCRC.crc_accumulate((byte) IMAVLinkCRC.MAVLINK_MESSAGE_CRCS[messageType], crc);
  byte crcl = (byte) (crc & 0x00FF);
  byte crch = (byte) ((crc >> 8) & 0x00FF);
  buffer[49] = crcl;
  buffer[50] = crch;
  return buffer;
}
public String toString() {
return "MAVLINK_MSG_ID_TERRAIN_DATA : " +   "  lat="+lat+  "  lon="+lon+  "  grid_spacing="+grid_spacing+  "  data="+data+  "  gridbit="+gridbit;}
}