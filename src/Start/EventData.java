package Start;

import java.io.IOException;


class EventData{
	public int state;
	public boolean compression_set=false;		// if compression is activated in general
	public boolean compressed=false;			// if this packet is compressed (datalen!=0)
	public Number packetLength;
	public Number dataLength;
	public Number packetId;
	public int bytes_read;
	public Client instance=null;
}
class EventResult{
	public boolean failed=true;
	public int bytes_read=0;
}

abstract class Event{
	public int packet_id=0x03;
	public int packet_state=1;
	public EventResult result=new EventResult();
	public Client i;	//instance
	public int active_id() {return packet_id;}
	public int active_state() {return packet_state;}
	public EventResult raise(EventData packet) {
		result.failed=false;
		result.bytes_read=packet.bytes_read;
		i=packet.instance;
		return run(packet);
	}
	public abstract EventResult run(EventData packet);
}

class TimedEvent{
	public long offset;
	public long creation_time;
	public void run() throws Exception {
	}
}