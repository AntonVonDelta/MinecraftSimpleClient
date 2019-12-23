package Start;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class Client {
	public ArrayList<TimedEvent> TimedEvents=new ArrayList<TimedEvent>();
	public  ArrayList<Event> bus=new ArrayList<Event>();
	public  DataOutputStream output;
	public  DataInputStream input;
	public  int state=0;
	public  boolean compression_set=false;
	public  Number maxPacketSize=null;
	public  Text UUID;
	public String username;
	public String password;
	
	public void openClient(SocketAddress proxy_address, ArrayList<Event> custom_bus,String ip,String mc_port,String name,String pass) throws IOException {
		int port=Integer.parseInt(mc_port);
		Random rand=new Random();
		boolean loop=true;
		
	    InetSocketAddress host = new InetSocketAddress(ip, port);
	    Socket socket;
	    if(proxy_address==null) {
	    	socket = new Socket();
	    }else {
		    Proxy proxy=new Proxy(Proxy.Type.SOCKS,proxy_address);
		    socket = new Socket(proxy);
	    }

	    username=name;
	    password=pass;
	    
	    socket.connect(host, 3000);
	    socket.setSoTimeout(10*1000);
	    m("Connecting...");

	    output = new DataOutputStream(socket.getOutputStream());
	    input = new DataInputStream(socket.getInputStream());

	    // Attempt login
	    byte [] handshakeMessage = createLoginPacket(ip, port);
	    writeVarInt(output, handshakeMessage.length);
	    output.write(handshakeMessage);
	    m("Handshaking....");
	    
	    // Login start
	    byte[] data_login=loginStart(name);
	    writeVarInt(output,data_login.length);
	    output.write(data_login);
	    m("Logging in with username "+name);
	    
	    
	    bus.add(new Packet_CompressionSet());
	    bus.add(new Packet_LoginSuccess());
	    bus.add(new Packet_Chat());
	    bus.add(new Packet_KeepAlive());
	    //bus.add(new Packet_SetSlot());
	    //bus.add(new Packet_WindowItems());
	    bus.add(new Packet_OpenWindow());
	    bus.add(new Packet_Disconnect());
	    bus.add(new Packet_LoginDisconnected());
	    
	    for(int i=0;custom_bus!=null && i<custom_bus.size();i++) {
	    	addEvent(custom_bus.get(i));
	    }
	    
	    try {
			while(loop) {
				EventData data=new EventData();
				EventResult result=null;
				data.state=state;
				data.compression_set=compression_set;
				data.instance=this;
				
			    for(int i=TimedEvents.size()-1;i>=0;i--) {
			    	TimedEvent temp=TimedEvents.get(i);
					try {
						if(Instant.now().getEpochSecond()-temp.creation_time>=temp.offset) {
							temp.run();
							TimedEvents.remove(i);
						}
					} catch (Exception e) {
						m("TimedEvent failed: "+e.getMessage());
						e.printStackTrace();
					}
			    }
				
				if(!compression_set) {
				    Number packetLen = readVarInt(input);
				    Number packetId= readVarInt(input);
				    data.packetId=packetId;
				    data.packetLength=packetLen;
				    data.bytes_read=packetId.byte_count;
				    
				    result=processPacket(data);
				}else {
				    Number packetLen = readVarInt(input);
				    Number dataLen= readVarInt(input);
				    
			    	data.dataLength=dataLen;
				    data.packetLength=packetLen;
				    data.bytes_read=dataLen.byte_count;
				    
				    if(dataLen.value==0) {
				    	Number packetId= readVarInt(input);
					    data.packetId=packetId;
					    data.bytes_read+=packetId.byte_count;
					    
					    result=processPacket(data);
				    }
				}
				
				if(result!=null) {
					if(result.failed) loop=false;
					else {
						readRemaining(data, result.bytes_read);
					}
				}else {
					readRemaining(data, data.bytes_read);
				}
			}
	    }catch(Exception ex) {
	    	m("Closing connection: "+ex.getMessage());
	    }
	    
	    m("Client closed");
		socket.close();
	}
	


	
	public EventResult processPacket(EventData data) {
		EventResult result=new EventResult();
		result.bytes_read=data.bytes_read;
		result.failed=false;
		
		for(int i=0;i<bus.size();i++) {
			Event temp=bus.get(i);
			if(temp.active_state()==data.state && temp.active_id()==data.packetId.value) {
				EventResult event_result=temp.raise(data);
				return event_result;
			}
		}
		
		//Here the packet wasn't proccessed
		//printPacketType(data.packetId.value, data.state);
		
		return result;
	}
	
	public void readRemaining(EventData data, int bytes_read) throws IOException {
		input.readNBytes(data.packetLength.value-bytes_read);
	}
	
	////////////////////////////////////////
	////////////////////////////////////////
//	 class Packet_ extends Event{
//		public Packet_() {packet_id=0x03;}
//		public EventResult run(EventData packet) {	
//			try {
//				
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			return result;
//		}
//
//	}
	public int clickCount=0;
	public int actionNumber=0;
	
	 class Packet_WindowItems extends Event{
		public Packet_WindowItems() {packet_id=0x14;}
		public EventResult run(EventData packet) {	
			try {
				m("Window Items");
				byte id=input.readByte();
				short count=input.readShort();
				
				m(id);
				m(count);
				
				if(clickCount==0) {
					clickCount++;
					writeVarInt(output, 2+2);
					writeVarInt(output, 0);
					writeVarInt(output, 0x1A);
					output.writeShort(1);	//2 bytes
					
					
					writeVarInt(output, 3);
					writeVarInt(output, 0);
					writeVarInt(output, 0x20);
					writeVarInt(output, 0);
				}
				
				result.bytes_read+=1+2;
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}
	}
	 class Packet_SetSlot extends Event{
		public Packet_SetSlot() {packet_id=0x16;}
		public EventResult run(EventData packet) {
			try {
				byte window_id=input.readByte();
				int slot=input.readShort();
				
				result.bytes_read+=3;
				
//				m("SetSlot");
//				m(window_id);
//				m(slot);
				
				// Click in order: 21+11+13
				if(slot==21 && clickCount==1) {
					clickCount++;
					
					byte[] slot_data=input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(output, 3+2+1+2+1+slot_data.length);
							writeVarInt(output, 0);
							writeVarInt(output, 0x07);
							output.writeByte(1);	// window id
							output.writeShort(21);	// slot number
							output.writeByte(0);	// button
							output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(output, 0);	// mode
							actionNumber++;

							output.write(slot_data);
							m("Clicked second slot");
						}
						
					});
				}
				if(slot==11 && clickCount==3) {
					clickCount++;
					
					byte[] slot_data=input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(output, 3+2+1+2+1+slot_data.length);
							writeVarInt(output, 0);
							writeVarInt(output, 0x07);
							output.writeByte(2);	// window id
							output.writeShort(11);	// slot number
							output.writeByte(0);	// button
							output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(output, 0);	// mode
							actionNumber++;

							output.write(slot_data);
							m("Clicked third slot");
						}
					});
				}
				if(slot==13 && clickCount==5) {
					
					byte[] slot_data=input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(output, 3+2+1+2+1+slot_data.length);
							writeVarInt(output, 0);
							writeVarInt(output, 0x07);
							output.writeByte(3);	// window id
							output.writeShort(13);	// slot number
							output.writeByte(0);	// button
							output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(output, 0);	// mode
							actionNumber++;

							output.write(slot_data);
							m("Clicked 4th  slot");
						}
					});
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}

	}
	
	 class Packet_OpenWindow extends Event{
		public Packet_OpenWindow() {packet_id=0x13;}
		public EventResult run(EventData packet) {
			try {
	    		
	    		Number windowId=readVarInt(input);
	    		Text type=readString(input);
	    		Text title=readString(input);
	    		byte number_slots=input.readByte();
	    		
	    		//m(windowId);
	    		//m(type);
	    		m("Opened window: "+title.value);
	    		//m("number slots: "+number_slots);
	    		
	    		result.bytes_read+=windowId.byte_count+type.byte_count+title.byte_count+1;
			} catch (Exception e) {
				e.printStackTrace();
			}

			return result;
		}
	}
		
	 class Packet_Disconnect extends Event{
		public Packet_Disconnect() {packet_id=0x1A;}
		public EventResult run(EventData packet) {	
			try {
				m("Disconnected");
				result.failed=true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}
	}
	 class Packet_CompressionSet extends Event{
		public Packet_CompressionSet() {packet_id=0x03;packet_state=0;}
		public EventResult run(EventData packet) {				
			compression_set=true;
			try {
				maxPacketSize = readVarInt(input);
				result.bytes_read+=maxPacketSize.byte_count;
				
				m("Received compression packet. maxPacketSize: "+maxPacketSize.value);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}
	}
	class Packet_LoginSuccess extends Event{
		public Packet_LoginSuccess() {packet_id=0x02;packet_state=0;}
		public EventResult run(EventData packet) {
			state=1;
		    
			try {
				UUID = readString(input);
				Text name=readString(input);
				
				m("Logged in. Details: ");
				m("   UUID:     "+UUID.value);
				m("   Username: "+name.value);
				
				result.bytes_read+=UUID.byte_count+name.byte_count;
			} catch (IOException e) {
				e.printStackTrace();
			}

			return result;
		}
	}
	 class Packet_LoginDisconnected extends Event{
		public Packet_LoginDisconnected() {packet_id=0x00;packet_state=0;}
		public EventResult run(EventData packet) {	
			try {
				m("Disconnected at login.");
				result.failed=true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}

	}
	
	class Packet_Chat extends Event{
		public Packet_Chat() {packet_id=0x0f;}
		public EventResult run(EventData packet) {
			try {
		    	Text msg=readString(input);
		    	String parsed=parseChatJson(msg.value);
		    	byte type=input.readByte();
		    	
		    	m(parsed);
		    	result.bytes_read+=msg.byte_count+1;
		    	
		    	
		    	if(parsed.contains("logheaza-te")) {
				    String comment="/login test19992004";
				    byte[] data_comment=createComment(comment);
				    writeVarInt(output,data_comment.length+1);	// packet data+packetid+datalen
				    writeVarInt(output,0);		// dataLen = 0
				    output.write(data_comment);
		    		
		    	}
		    	
			} catch (IOException e) {
				e.printStackTrace();
			}

			return result;
		}
	}
	class Packet_KeepAlive extends Event{
		public Packet_KeepAlive() {packet_id=0x1f;}
		public EventResult run(EventData packet) {
			try {
	    		long id=input.readLong();
	    		writeVarInt(output,8+2);
	    		writeVarInt(output,0);
	    		writeVarInt(output, 0x0b );
	    		output.writeLong(id);
	    		
	    		result.bytes_read+=8;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return result;
		}
	}
	

	
	
	
	/////////////////////////////////////
	/////////////////////////////////////
	public void addEvent(Event event) {
    	boolean found=false;
		
    	for(int j=0;j<bus.size();j++) {
    		Event temp2=bus.get(j);
    		if(event.packet_id==temp2.packet_id && event.packet_state==temp2.packet_state) {
    			found=true;
    			bus.set(j, event);
    			break;
    		}
    	}
    	if(!found) bus.add(event);
	}
	public void addTimedEvent(int offset,TimedEvent event) {
		event.creation_time=Instant.now().getEpochSecond();
		event.offset=offset;
		TimedEvents.add(event);
	}
    
	public static String parseChatJson(String txt) {
		String parts[]=txt.split("\"");
		String res="";
		
		for(int i=0;i<parts.length-1;i++) {
			if(parts[i].contentEquals("text") || parts[i].contentEquals("extra") ) {
				if(parts[i+1].contains("{")) continue;
				res+=parts[i+2];
				i+=2;
			}
		}
		return res;
	}
	
	public static byte[] createComment(String txt) throws IOException {
	    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	    DataOutputStream packet = new DataOutputStream(buffer);
	    writeVarInt(packet, 0x02); //packet id for chat
	    writeString(packet, txt, StandardCharsets.UTF_8);

	    return buffer.toByteArray();
	}
	public static byte [] loginStart(String name) throws IOException {
	    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	    DataOutputStream packet = new DataOutputStream(buffer);
	    writeVarInt(packet, 0x00); //packet id for handshake
	    writeString(packet, name, StandardCharsets.UTF_8);

	    return buffer.toByteArray();
	}
	public static byte [] createLoginPacket(String host, int port) throws IOException {
	    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	    DataOutputStream handshake = new DataOutputStream(buffer);
	    writeVarInt(handshake, 0x00); //packet id for handshake
	    writeVarInt(handshake, 340); //protocol version
	    writeString(handshake, host, StandardCharsets.UTF_8);
	    handshake.writeShort(port); //port
	    writeVarInt(handshake, 2); //state (1 for handshake; 2 for login)

	    return buffer.toByteArray();
	}

	public static void writeString(DataOutputStream out, String string, Charset charset) throws IOException {
	    byte [] bytes = string.getBytes(charset);
	    writeVarInt(out, bytes.length);
	    out.write(bytes);
	}
	public static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
	    while (true) {
	        if ((paramInt & 0xFFFFFF80) == 0) {
	          out.writeByte(paramInt);
	          return;
	        }

	        out.writeByte(paramInt & 0x7F | 0x80);
	        paramInt >>>= 7;
	    }
	}
	public static Number readVarInt(DataInputStream in) throws IOException {
	    int i = 0;
	    int j = 0;
	    int byte_count=0;
	    while (true) {
	        int k = in.readByte();
	        byte_count++;
	        i |= (k & 0x7F) << j++ * 7;
	        if (j > 5) throw new RuntimeException("VarInt too big");
	        if ((k & 0x80) != 128) break;
	    }
	    return new Number(i,byte_count);
	}
	public static Text readString(DataInputStream input) throws IOException {
		Number nr=readVarInt(input);
		int size=nr.value;
		int byte_count=nr.byte_count+size;
		
	    byte[] buff = new byte[size];
	    input.readFully(buff);  		//read json string
	    String txt = new String(buff);
	    
	    return new Text(txt,byte_count);
	}
	
	public static String generateName() {
		Random rand=new Random();
		int r=rand.nextInt(26);
		String name=Character.toString((char)(r+97));
		
		int rand_var=1+rand.nextInt(15);
		for(int i=0;i<7;i++) {
			r+=rand_var;
			r=r^90;
			r*=47;
			r=Math.abs(r);
			r%=800000;
			
			name+=Character.toString((char)((r%26)+97));
		}
		
		return name;		
	}
	public static void m(String a) {
		System.out.println(a);
	}
	public static void m(int a) {
		System.out.println(a);
	}
	public static void m(long a) {
		System.out.println(a);
	}
	public static void m(Text a) {
		System.out.println(a.value);
	}
	public static void m(Number a) {
		System.out.println(a.value);
	}
	public static void m(Object a) {
		System.out.println(a.toString());
	}

	public static void printPacketType(Number id,int state) {
		printPacketType(id.value,state);
	}
	public static void printPacketType(int id,int state) {
		String arr[];
		if(state==0) {
			arr= new String[] {"Disconnected","Encryption Request","Login Success","Compression enable","Plugin"};
		}else {
			arr= new String[] {	"Spawn","Orb","Spawn Global Entity","Spawn Mob","",
								"Spawn Player","Animation","Statistics","Block Break Animation","Update Block Entity",
								"Block Action","Block Change","Boss Bar","Server Difficulty","Chat Message"};
		}
		
		HashMap<Integer, String> translate=new HashMap<Integer, String>();
		for(int i=0;i<arr.length;i++) translate.put(i, arr[i]);
		
		translate.put(0x26,"Entity Relative Move");
		translate.put(0x2f,"Player Position And Look ");
		translate.put(0x36,"Entity Head Look");
		translate.put(0x3c,"Entity Metadata");
		translate.put(0x22,"Particle");
		translate.put(0x4c,"Entity Teleport");
		translate.put(0x27,"Entity Look And Relative Move");
		translate.put(0x18,"Plugin Message ");
		translate.put(0x23,"Join Game");
		translate.put(0x0f,"Chat Message ");
		translate.put(0x46,"Spawn Position");
		translate.put(0x2C,"Player Abilities  ");
		translate.put(0x3A,"Held Item Change  ");
		translate.put(0x44,"Teams ");
		translate.put(0x13,"Open Window ");
		translate.put(0x1A,"Disconnected ");
		
		if(translate.containsKey(id)) {
			m(translate.get(id));
		}else m("UNKNWON packet");
	}
}
