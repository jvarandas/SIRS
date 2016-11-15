package Bank.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class Server {
	private static DatagramSocket socket;
	
	public static void main(String[] args) throws Exception {
		System.out.println("Server started running");
		byte[] buffer = new byte[120];
		socket = new DatagramSocket(10100);
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		
		System.out.println("Running - Awaiting Messages");
		System.out.println("Press enter to shutdown");
		
		while(true){
			socket.receive(packet);
			String out = new String(packet.getData());
			System.out.println("Recived: "+ out);
			//Thread.sleep(1000);
			sendAck(packet.getAddress(), packet.getPort());
		
		}
	}
	
	public static void sendAck(InetAddress address, int port) throws IOException{
	    byte[] ackPacket = new byte[2];
	    ackPacket[0] = (byte)(1);
	    ackPacket[1] = (byte)(1);
	    DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Sent ack");
}

}
