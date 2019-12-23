package Start;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import Start.Number;
import Start.Text;

public class MainClass {	
	public static MainClass me=new MainClass();
	public static boolean registered=false;
	public static boolean sent_register_command=false;
	public static boolean loggedin=false;
	public static boolean sent_login_command=false;
	public static boolean enteredGame=false;
	
	public static void main(String [] args) throws IOException {
		registerAndLoginUser(null,"mc.gamster.org","25565","UserName","Pass");
	}
	
	public static boolean registerAndLoginUser(SocketAddress proxy_address,String address,String mc_port,String name,String password) throws IOException {
		Client register=new Client();
		Client login=new Client();
		
		register.addEvent(me.new Packet_RegisterChat());
		register.openClient(proxy_address, null, address, mc_port, name, password);
		
		if(registered) {
			login.addEvent(me.new Packet_SetSlot());
			login.addEvent(me.new Packet_WindowItems());
			login.addEvent(me.new Packet_LoginChat());
			login.openClient(null, null, address, mc_port, name, password);
			
			if(loggedin) {
				m("\n\nSuccesfully registered, logged in and entered bedwars-doubles!\n\n");
				return true;
			}
		}
		return false;
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
	
	class Packet_RegisterChat extends Event{
		public Packet_RegisterChat() {packet_id=0x0f;}
		public EventResult run(EventData packet) {
			String reason=null;
			
			try {
		    	Text msg=readString(i.input);
		    	String parsed=parseChatJson(msg.value);
		    	byte type=i.input.readByte();
		    	
		    	result.bytes_read+=msg.byte_count+1;
		    	
		    	m(parseChatJson(msg.value));
		    	
		    	if(msg.value.contains("Te rugam, logheaza-te cu comanda")) {
		    		reason="Already registered this name.";	// asks us to login thus the username exists already
		    		registered=true;
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("logat pe baza sesiunii anterioare")) {
		    		reason="Already registered this name. Already logged in!";
		    		registered=true;
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("Te-ai inregistrat cu succes")) {
		    		reason="Registered succesfully!";
		    		registered=true;
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("Ai depasit")) {
		    		reason="Change Username. Limit crossed!";
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("Parola ta este prea scurta sau prea lunga")) {
		    		reason="Password refused!";
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("inregistrezi folosind comanda") && !sent_register_command) {
				    String comment="/register "+i.password+" "+i.password;
				    byte[] data_comment=createComment(comment);
				    writeVarInt(i.output,data_comment.length+1);	// packet data+packetid+datalen
				    writeVarInt(i.output,0);		// dataLen = 0
				    i.output.write(data_comment);
				    
				    sent_register_command=true;
				    m("------ > Sending /register command...");
		    	}
		    	
			} catch (IOException e) {
				e.printStackTrace();
			}

			return result;
		}
	}
	
	class Packet_LoginChat extends Event{
		public Packet_LoginChat() {packet_id=0x0f;}
		public EventResult run(EventData packet) {	
			try {
		    	Text msg=readString(i.input);
		    	String parsed=parseChatJson(msg.value);
		    	byte type=i.input.readByte();
		    	
		    	//Messages used for ping look like this: 'Nume: §b§lqikcygyi'
		    	if(!parsed.contains("Nume: ")){
		    		m(parsed);
		    	}
		    	result.bytes_read+=msg.byte_count+1;
		    	
		    	if(msg.value.contains("Serverul este plin") || msg.value.contains("bedwars")) {
		    		enteredGame=true;
		    		result.failed=true;
		    	}
		    	if(msg.value.contains("logat pe baza sesiunii anterioare")) {
		    		loggedin=true;
		    	}
		    	if(msg.value.contains("Esti deja logat pe server")) {
		    		result.failed=true;	// Can't login because I am still on the server
		    	}
		    	if(msg.value.contains("Te-ai logat cu succes")) {
		    		loggedin=true;
		    	}
		    	if(msg.value.contains("Te rugam, logheaza-te cu comanda") && !sent_login_command) {
				    String comment="/login "+i.password;
				    byte[] data_comment=createComment(comment);
				    writeVarInt(i.output,data_comment.length+1);	// packet data+packetid+datalen
				    writeVarInt(i.output,0);		// dataLen = 0
				    i.output.write(data_comment);
				    
				    //sent_login_command=true;
				    m("------ > Sending /login command...");
		    	}
		    	
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}

	}
	 
	 class Packet_WindowItems extends Event{
		public Packet_WindowItems() {packet_id=0x14;}
		public EventResult run(EventData packet) {	
			try {
				m("Window Items");
				byte id=i.input.readByte();
				short count=i.input.readShort();

				result.bytes_read+=1+2;
				//m(id);
				//m(count);
				
		    	//Right click on hotbar item
		    	if(loggedin) {
					if(clickCount==0) {
						clickCount++;
						
						i.addTimedEvent(3, new TimedEvent() {
							@Override
							public void run() throws IOException {
								writeVarInt(i.output, 2+2);
								writeVarInt(i.output, 0);
								writeVarInt(i.output, 0x1A);
								i.output.writeShort(1);	//2 bytes
								
								
								writeVarInt(i.output, 3);
								writeVarInt(i.output, 0);
								writeVarInt(i.output, 0x20);
								writeVarInt(i.output, 0);
							}
						});
					}
		    	}
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
				byte window_id=i.input.readByte();
				int slot=i.input.readShort();
				
				result.bytes_read+=3;
				
				// Click in order: 21+11+13 to enter bedwars game
				if(slot==21 && clickCount==1) {
					clickCount++;
					
					byte[] slot_data=i.input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					i.addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(i.output, 3+2+1+2+1+slot_data.length);
							writeVarInt(i.output, 0);
							writeVarInt(i.output, 0x07);
							i.output.writeByte(1);	// window id
							i.output.writeShort(21);	// slot number
							i.output.writeByte(0);	// button
							i.output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(i.output, 0);	// mode
							actionNumber++;

							i.output.write(slot_data);
							m("Clicked second slot");
						}
					});
				}
				if(slot==11 && clickCount==3) {
					clickCount++;
					
					byte[] slot_data=i.input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					i.addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(i.output, 3+2+1+2+1+slot_data.length);
							writeVarInt(i.output, 0);
							writeVarInt(i.output, 0x07);
							i.output.writeByte(2);	// window id
							i.output.writeShort(11);	// slot number
							i.output.writeByte(0);	// button
							i.output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(i.output, 0);	// mode
							actionNumber++;

							i.output.write(slot_data);
							m("Clicked third slot");
						}
					});
				}
				if(slot==14 && clickCount==5) {
					
					byte[] slot_data=i.input.readNBytes(packet.packetLength.value-result.bytes_read);
					result.bytes_read=packet.packetLength.value;
					i.addTimedEvent(2 , new TimedEvent() {
						@Override
						public void run() throws Exception {
							clickCount++;
							
							writeVarInt(i.output, 3+2+1+2+1+slot_data.length);
							writeVarInt(i.output, 0);
							writeVarInt(i.output, 0x07);
							i.output.writeByte(3);	// window id
							i.output.writeShort(14);	// slot number
							i.output.writeByte(0);	// button
							i.output.writeShort(actionNumber);	//action number unique- for transactions
							writeVarInt(i.output, 0);	// mode
							actionNumber++;

							i.output.write(slot_data);
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