import java.net.DatagramPacket;
import java.net.DatagramSocket;


public class Server {

	private final static int PORT = 8080;
	public static void main(String[] args) throws Exception {
		System.out.println("Server started running");
		byte[] buffer = new byte[120];
		DatagramSocket socket = new DatagramSocket(PORT);
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while(true){
			socket.receive(packet);
			String out = new String(packet.getData());
			System.out.println(out);
		}
	}

}
