import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;


public class Client {
	
	private final static int PORT = 8080;

	public static void main(String[] args) {
		System.out.println("Client started running...");
		Scanner in = new Scanner(System.in);
		for(String input = in.nextLine(); !(input.equals("EXIT")); input = in.nextLine()){
			if(input.startsWith("SEND")){
				try {
					sendMessage(input);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		in.close();
	}

	private static void sendMessage(String input) throws Exception {
		InetAddress addr = InetAddress.getLocalHost();
		DatagramSocket socket = new DatagramSocket();
		byte[] buffer = new byte[120];
		buffer = input.getBytes();
		DatagramPacket packet = new DatagramPacket(buffer,buffer.length, addr, PORT);
		socket.send(packet);
		
	}

}
