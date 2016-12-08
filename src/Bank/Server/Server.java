package Bank.Server;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

import Bank.Server.Exceptions.DHMessageException;
import Bank.Server.Exceptions.DataSizeException;



public class Server {
	private static File _config;
	private static DatagramSocket socket;
	private static Map<String, Integer> Bank = new ConcurrentHashMap<String, Integer>(); //Emulate the bank
	private static Map<SocketAddress, String> Contacts = new ConcurrentHashMap<SocketAddress, String>(); //Associated addrs for each account
	private static Map<String,List<String>> ClientsMatrix = new ConcurrentHashMap<String,List<String>>();
	private static Map<Integer, String> ClientsPhoneNumbers = new ConcurrentHashMap<Integer, String>(); 
	
	private static SecureRandom randomizer = new SecureRandom();
	
	public static void main(String[] args) throws Exception {
		System.out.println("Server started running");
		_config = new File("bank.cnf");
		
		Scanner in = new Scanner(System.in);
		for(String input = in.nextLine(); !(input.toLowerCase().equals("start")); input = in.nextLine()){
			if(input.toLowerCase().startsWith("create ")){
				String writtenCommand = input.substring(input.indexOf(" ")+1, input.length());
				createUser(writtenCommand);
			}
		}
	
		for(String client : Bank.keySet()){
			System.out.println(client);
		}
		
		byte[] buffer = new byte[120];
		socket = new DatagramSocket(10100);
		
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		
		System.out.println("Running - Awaiting Messages");
		
		socket.setSoTimeout(0);
		while(true){
			socket.receive(packet);
			String message = new String(packet.getData());
			assignPort(message, packet);
			/*if (validateTimestamp(message) && validateID(message)){
				parseMessage(packet);
			}*/
		}
	}
	
	private static void createUser(String writtenCommand) throws IOException {
		String[] split = writtenCommand.split(" ");
		String iban = split[0]; 
		int amount = Integer.parseInt(split[1]);
		if(iban.length() != 25 || amount <= 0){
			System.out.println("User not created");
			return;
		}
		Bank.put(iban, amount);
		MatrixCard d = new MatrixCard(iban);
		ClientsMatrix.put(iban, d.getContent());
		confFile(); //flush Bank Hashmap to file
	}

	private static void assignPort(String message, DatagramPacket clientPacket) throws IOException{
		String[] content = message.split("\\|\\|");
		if(content[0].equals("request")){
			int port = randomizer.nextInt(15535);
			port+=50000; //PORTAS DE 50000 ATE 65535
			Message m = new Message(port);
			byte[] portPacket = m.getMessage().getBytes();
			
			DatagramPacket packet = new DatagramPacket(portPacket, portPacket.length,clientPacket.getAddress(), clientPacket.getPort());
			DatagramSocket clientSocket = new DatagramSocket(port);
			clientSocket.send(packet);
			
			ClientServiceThread cliThread = new ClientServiceThread(clientSocket, Bank, Contacts, ClientsMatrix, ClientsPhoneNumbers);
			cliThread.start();
			System.out.println("Client has joined in port " + port);
		}
	}

	private static void confFile() throws IOException{
		
		BufferedWriter output = null;
		String titulo = new String();
		String text = new String();
		int saldo;
		
		try {
			FileOutputStream fos = new FileOutputStream(_config);
			
			output = new BufferedWriter(new OutputStreamWriter(fos));
			
			titulo = "Iban\t\t\t\tSaldo";
			
			for(String iban: Bank.keySet()){
				saldo = Bank.get(iban);
				
				text += iban+"\t"+saldo+"\n";
			}
			
			output.write(titulo);
			output.newLine();
			output.write(text);
			output.close();
			
		} catch ( IOException e ) {
			e.printStackTrace();
			
		}
	}
}

class ClientServiceThread extends Thread{
	private Map<String, Integer> Bank;
	private Map<SocketAddress, String> Contacts; //Associated addrs for each account
	private Map<String,List<String>> ClientsMatrix;
	private Map<Integer, String> ClientsPhoneNumbers;
	private static List<Long> ID_Bucket = new ArrayList<Long>();  //to save the received IDS

	private DatagramSocket socket;
	private SocketAddress socketClient;
	
	private static byte[] Confirmation_Ack = 	new byte[] {0,0};	//transfer completed
	private static byte[] Amount_Error_Ack = 	new byte[] {1,1};	//amount not available 
	private static byte[] Source_Unknown_Ack = new byte[] {1,0};		//source iban not found
	private static byte[] Dest_Unknown_Ack = 	new byte[] {0,1};	//dest iban not fount
	private static byte[] Not_Authorized_Ack = new byte[] {2,0};		//source iban doesnt belong to that port
	
	private static long Max_Time_Diff = 2; //max difference of 2 seconds
	private static SecureRandom randomizer = new SecureRandom();
	
	private static BigInteger a = new BigInteger(10, randomizer);
	private static String sessionKey = "1234567891234567";
	
	//private static AES cbc;
	
	ClientServiceThread(DatagramSocket socket, Map<String, Integer> Bank, Map<SocketAddress, String> Contacts, Map<String,List<String>> ClientsMatrix, Map<Integer, String> ClientsPhoneNumbers){
		this.socket = socket;
		this.Bank= Bank;
		this.Contacts = Contacts;
		this.ClientsMatrix = ClientsMatrix;
		this.ClientsPhoneNumbers = ClientsPhoneNumbers;
	}
	
	@Override
	public void run() {
		
		try {			
			//generateDHValues();
			//cbc = new AES(sessionKey);
			byte[] buffer = new byte[120];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		
			socket.setSoTimeout(0);
			while(true){
				socket.receive(packet);
				String received = new String(packet.getData());
				System.out.println(received.length());
				//String message = cbc.decrypt(received);
				String message = received;
				if (validateID(message)){
					parseMessage(packet);
				}
			}		
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void generateDHValues() throws Exception{
		
		BigInteger p = collectDHValues();
		
		BigInteger q = collectDHValues();
		
		BigInteger yB = collectDHValues();
		
		BigInteger yA = new BigInteger(10, randomizer);
		
		int bitLength = 1024; // 1024 bits
	    
	    a = BigInteger.probablePrime(bitLength, randomizer);
	    yA = p.modPow(a, q);
	    
	    List<byte[]> messages = computeDHMessage(q);
	    sendDHMessage(messages);
	    
	    
	    messages = computeDHMessage(yA);
	    System.out.println(yA);
	    sendDHMessage(messages);
	    
	    //sessionKey = yB.modPow(a, q);
	   
	    System.out.println("THE KEY: "+ sessionKey);
	}
	
	private List<byte[]> computeDHMessage(BigInteger n) throws DataSizeException{
		byte[] nBytes = n.toByteArray();
		List<byte[]> res = new ArrayList<byte[]>();
		byte[] code = new byte[129];
		
		code = Arrays.copyOfRange(nBytes, 0, nBytes.length);
		
		res.add(code);
		
		return res;
	}
	
	private BigInteger collectDHValues() throws IOException{
		
		ByteArrayOutputStream aux = new ByteArrayOutputStream();
		
		byte[] keys = new byte[129];
		DatagramPacket keysPacket = new DatagramPacket(keys, keys.length);
		socket.receive(keysPacket);

		aux.write(Arrays.copyOfRange(keysPacket.getData(), 0, keysPacket.getData().length));
		
		socketClient = keysPacket.getSocketAddress();

		
		return new BigInteger(aux.toByteArray());
	}
	
	private void sendDHMessage(List<byte[]> byteList) throws IOException, DHMessageException{
		
		for(byte[] m: byteList){
			DatagramPacket keysPacket = new DatagramPacket(m, m.length, socketClient);
			System.out.println("ENVIADO: "+keysPacket.getData().length);
			socket.send(keysPacket);
		}
	}
	
	/*private boolean validateTimestamp(String msg){
		String[] content = msg.split("\\|\\|");
		String date = content[content.length-1].substring(0, 19);
		Date timestamp;
		try {
			SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			timestamp = parser.parse(date);
			LocalDateTime current_time = LocalDateTime.now();
			LocalDateTime limit_time = current_time.minusSeconds(Max_Time_Diff);
			LocalDateTime stamp = LocalDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
			if (stamp.isAfter(limit_time) && stamp.isBefore(current_time))
				return true;
			else 
				return false;
		} catch (ParseException e) {
			System.out.println("erro no parse" + e.getErrorOffset());
			return false;
		}
	}*/
	
	private boolean validateID(String msg){
		long id = Long.parseLong(msg.split("\\|\\|")[1]);
		 if (!ID_Bucket.contains(id)){
			 ID_Bucket.add(id);
			 return true;
		 }
		 else return false;
	}
	
	private boolean parseMessage(DatagramPacket packet) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException{
		String received = new String(packet.getData());
		
		System.out.println(received.length());
		
		//String msg = cbc.decrypt(received);
		String msg = received;
		
		SocketAddress sender = packet.getSocketAddress();
		String[] content = msg.split("\\|\\|");
		String type = content[0];
		String data = content[2];
		if (type.equals("associate")){  //TO register the "phone number" associated with an account 
			
			String[] association = data.split(" "); 
			String iban = association[0];			//Since client ports are not fixed we need to register them at the beginning of each run
			int number = Integer.parseInt(association[1]);
			
			if(ClientsPhoneNumbers.containsKey(number)){
				System.out.println("Number " + number + " exists associated with another account");
				sendAck(packet.getAddress(), packet.getPort(), Not_Authorized_Ack, Long.parseLong(content[1]));
				return false;
			}
			else if(ClientsPhoneNumbers.containsValue(iban)){
				System.out.println("Iban " + iban + " exists associated with another account");
				sendAck(packet.getAddress(), packet.getPort(), Not_Authorized_Ack, Long.parseLong(content[1]));
				return false;
			}
			else if(!Bank.containsKey(iban)){
				System.out.println("Iban " + iban + " does not exist in this Bank");
				sendAck(packet.getAddress(), packet.getPort(), Source_Unknown_Ack, Long.parseLong(content[1]));
				return false;
			}
			
			sendAck(packet.getAddress(), packet.getPort(), Confirmation_Ack, Long.parseLong(content[1]));
			
			if(!confirmsIdentity(iban,packet)){
				return false;
			}
			
			Contacts.put(sender, iban);
			ClientsPhoneNumbers.put(number, iban);
			confFile(); //flush Bank Hashmap to file
			System.out.println("Association successful");
			return true;
			
		}else if(type.equals("send")){
			String[] transaction = data.split(" ");			

			String destAccount = transaction[0];
			int amount = Integer.parseInt(transaction[1]);
			int number = Integer.parseInt(transaction[2]);

			//get IBAN from sender addr
			String sourceAccount = ClientsPhoneNumbers.get(number);
			
			
			 return processTransfer(sourceAccount, destAccount, amount, packet);
		}
		
		
		return false;
	}
	
	
	
	private boolean confirmsIdentity(String iban, DatagramPacket packet) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException{
		ArrayList<String> matrix = (ArrayList<String>) ClientsMatrix.get(iban);
		int[][] pos = new int[4][2];
		char[] chars = new char[4];
		for(int i = 0; i < 4; i++){
			int c = randomizer.nextInt(10); //column
			int l = randomizer.nextInt(10); //line
			pos[i][0] = c;
			pos[i][1] = l;
			char ch = matrix.get(l).charAt(c);
			chars[i] = ch;
		}
		String code = new String(chars);
		System.out.println(code);
		Message m = new Message(pos[0], pos[1], pos[2], pos[3]); //Message with positions for matrix
		byte[] positions = m.getMessage().getBytes();
		DatagramPacket positionPacket = new  DatagramPacket(positions, positions.length, packet.getAddress(), packet.getPort());
		socket.send(positionPacket);
		
		byte[] ack = new byte[120];
        DatagramPacket codePacket = new DatagramPacket(ack, ack.length);
		
		try {
			socket.setSoTimeout(40000);//Waits 40seconds for code
			socket.receive(codePacket);
			socket.setSoTimeout(0);
			
			String received = new String(codePacket.getData());
			//String msg = cbc.decrypt(received);
			String msg = received;
			
			if (!validateID(msg)){
				return false;
			}
			String content[] = msg.split("\\|\\|");
			String type = content[0];
			if(!type.equals("codes_answer")){
				sendAck(codePacket.getAddress(), codePacket.getPort(), Not_Authorized_Ack, Long.parseLong(content[1]));
				return false;
			}
			String data = content[2];
			if(!data.equals(code)){
				sendAck(codePacket.getAddress(), codePacket.getPort(), Not_Authorized_Ack, Long.parseLong(content[1]));
				return false;
			}
			sendAck(codePacket.getAddress(), codePacket.getPort(), Confirmation_Ack, Long.parseLong(content[1]));
			return true;
			
			
		}catch (SocketTimeoutException e){
			System.out.println("Client did not respond in time.");
			System.out.println("Process canceled");
			return false;
		}
	}
	
	private boolean processTransfer(String source, String dest, int ammount, DatagramPacket packet) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException{
		String msg = new String(packet.getData());
		SocketAddress sender = packet.getSocketAddress();
		String[] content = msg.split("\\|\\|");
		
		if (!Bank.containsKey(dest)){
			sendAck(packet.getAddress(), packet.getPort(), Dest_Unknown_Ack, Long.parseLong(content[1]));
		}
		
		else if (!Bank.containsKey(source)){
			sendAck(packet.getAddress(), packet.getPort(), Source_Unknown_Ack, Long.parseLong(content[1]));
		}
		
		else if (Bank.get(source)<ammount){
			sendAck(packet.getAddress(), packet.getPort(), Amount_Error_Ack, Long.parseLong(content[1]));
		}
		
		else {
			sendAck(packet.getAddress(), packet.getPort(), Confirmation_Ack, Long.parseLong(content[1]));
			if(!confirmsIdentity(source, packet)){
				return false;
			}
			Bank.put(source, Bank.get(source)-ammount);
			Bank.put(dest, Bank.get(dest)+ammount);
			confFile(); //flush Bank Hashmap to file
		    return true;
		}
		return false;
	}
	
	private void sendAck(InetAddress address, int port, byte[] ackPacket, long ID) throws IOException{
		Message ack = new Message(ackPacket);
		ack.setID(ID);
		byte[] bytesAck = ack.getMessage().getBytes();
	    DatagramPacket acknowledgement = new  DatagramPacket(bytesAck, bytesAck.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Sent ack");
	}


	private void confFile() throws IOException{
		
		BufferedWriter output = null;
		String titulo = new String();
		String text = new String();
		int saldo;
		
		try {
			FileOutputStream fos = new FileOutputStream("bank.cnf");
			
			output = new BufferedWriter(new OutputStreamWriter(fos));
			
			titulo = "Iban\t\t\t\tSaldo";
			
			for(String iban: Bank.keySet()){
				saldo = Bank.get(iban);
				
				text += iban+"\t"+saldo+"\n";
			}
			
			output.write(titulo);
			output.newLine();
			output.write(text);
			output.close();
			
		} catch ( IOException e ) {
			e.printStackTrace();
			
		}
	}

}