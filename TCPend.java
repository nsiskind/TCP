import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;


//primitive array buffer
//thread creates and maintains a buffer 
//each have a time stamp
//pointer for the last acked

//when a buffer order
//need out over order
//send file name in the beginning

//third dup ack
//time out
//mtu = packesize plus data

public class TCPend{

	static int max_transmit_unit = 0;

	//will refer to the packets not bytes!
	static int seq = 0;
	static boolean threadStop = false;
	static int ack = 0;
	static int timeout = 5;
	static ArrayList<tcpPacket> bufTransfer = new ArrayList<tcpPacket>();
	static ArrayList<tcpPacket> bufResponse = new ArrayList<tcpPacket>();
	static ArrayList<tcpPacket> bufRecieve = new ArrayList<tcpPacket>();
	static byte [] file;
	static int lastAck;
	static int numUnAck = 0;
	static int slideWindow = 0;
	static int ertt = 0;
	static int edev = 0;

	//Things to print at the end!
	static int packetsSent = 0;
	static int packetsRecieved = 0;
	static int packetsDiscarded = 0;
	static int numRetransmit = 0;
	static int numDupAck = 0;
	static int dataTransferred = 0;


	//Handles bad argument
	public static void argError(){
		System.out.println("Error: missing or additional arguments");
		System.exit(0);
	}

	//handles client stuff
	public synchronized static void doTransfer(String fileName, int portNum, String remoteIP, int remotePort) throws IOException, ClassNotFoundException, InterruptedException {

		int dataSize = max_transmit_unit-24; 
		File theFile = new File(fileName);
		int sizeOfFile = ((int) theFile.length());// + theFile.getName().length();

		//		System.out.println("file Name = " + theFile.getName() +  "file should be = " + fileName);

		//Puts file in byte array to send
		file = fileToByteArray(fileName);

		//number of packets = total bytes/( how many data bytes per packets )
		int numOfPacket = (int)Math.ceil((sizeOfFile)/(max_transmit_unit-24));

		System.out.println("num packets = " + numOfPacket + "size of file " + sizeOfFile);
		//tracks data written
		int written = 0;

		//Makes a new socket to the ip at the port, IP needs to be a string
		Socket socket = new Socket(remoteIP, remotePort);

		//Makes output/input stream to read from
		final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
		final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

		openConnection(in, out);

		//Send first packet with only the name
		out.writeObject(new tcpPacket(seq++, 0, System.nanoTime(), max_transmit_unit,
				(short) 0, (short)0, fileName.getBytes()));	
		printInfo("snd", System.currentTimeMillis(), "D", seq-1, fileName.getBytes().length, 0);
		packetsSent++;
		dataTransferred+=fileName.getBytes().length;

		Thread thread = new Thread(){public void run(){
			while(true){
				try {
					if(threadStop){
						break;
					}
					if(numUnAck > 0){
						//						System.out.println("num un ack " + numUnAck);

						Thread.sleep(10);
						Object obj = in.readObject();
						tcpPacket packet = (tcpPacket) obj;
						printInfo("rcv", System.currentTimeMillis(), "A", 0, 0, packet.getAcknowledge());
						bufResponse.add(packet);
						computeTimeout(packet.getSequence(), packet.getTimeStamp(), System.nanoTime());
						//						System.out.println("buf Transfer "  + bufTransfer);
						//						System.out.println("bufResponse " + bufResponse);
						System.out.print("");
						System.out.print("");
						System.out.print("");

					}
					for(int i = 0; i < bufTransfer.size(); i++){
						boolean found = false;
						for(int j = 0; j < bufResponse.size(); j++){
							if(bufTransfer.get(i).getSequence() == bufResponse.get(j).getAcknowledge()){
								numUnAck--;
								bufTransfer.remove(i);
								found = true;
							}
						}
						System.out.print("");
						System.out.print("");
						System.out.print("");

						System.out.print("");
						if(!found && ((bufTransfer.get(i).getTimeStamp() + (timeout*1000000000)) < System.nanoTime())){
							//reset send time
							bufTransfer.get(i).setTimeStamp(System.nanoTime());
							resend(bufTransfer.get(i), out);
							numRetransmit++;
							//							System.out.println("resend buftransfer " +bufTransfer);
						}

					}
					Thread.sleep(1000);
					//					System.out.println("STILL IN THE THREAD");

				} catch (ClassNotFoundException | IOException e) {
					System.out.println("reading object error in thread");

					e.printStackTrace();
					break;
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				}
			}
		}};

		//		System.out.println("BEFORE THREAD");

		thread.start();
		//		System.out.println("STARTED THREAD");

		//		System.out.println("seq = " + seq);

		//while havent finished writting the file
		//		seq = 0;

		while(written < sizeOfFile){
			System.out.print("");
			//Code for sending a packet and waiting for timeout
			if(numUnAck < slideWindow){

				//NEED TO WRITE CHECKSUM METHOD

				tcpPacket sendPack = null; 
				//normally

				//Its seq-2 b/c first seq is for syn
				if((seq-2) <= numOfPacket){
					sendPack = new tcpPacket(seq, 0, System.nanoTime(), max_transmit_unit,
							(short) 0, (short)0, Arrays.copyOfRange(file, ((seq-2)*dataSize), ((seq-2)*dataSize)+dataSize)
							);	
					//										System.out.println("sent :" + ((seq-2)*dataSize) +" to "+ (((seq-2)*dataSize)+dataSize));
					seq++;
					//increment seq by  #bytes that we sent
					written+=dataSize;
					//										System.out.println("written " + written);
					System.out.print("");
					System.out.print("");

				}

				//seq + dataSize > file length, so were probably at the ende
				else{

					sendPack = new tcpPacket((seq-2), 0, System.nanoTime(), max_transmit_unit,
							(short) 0, (short) 0, Arrays.copyOfRange(file, written, file.length)
							);

					System.out.print("");
					System.out.print("");

					//										System.out.println("sent :" + ((seq-2)*dataSize)+ " to "+ file.length);
					seq++;

				}

				//add to buffer and increment buf pointer
				//send it!
				//sendPack.setChecksum();
				out.flush();

				sendPack(sendPack,out);
				printInfo("snd", System.currentTimeMillis(), "D", sendPack.getSequence(),
						sendPack.getData().length, sendPack.getAcknowledge());
				out.flush();

				Thread.sleep(1000);
			}
		}

		threadStop = true;
		//		System.out.println("sent the whole file");
		Thread.sleep(1000);

		closeConnectionSender(in, out, socket);
	}

	public static void sendPack(tcpPacket packet, ObjectOutputStream out){

		try {
			out.writeObject(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}

		bufTransfer.add(packet);
		numUnAck++;
		dataTransferred+=packet.getData().length;
		packetsSent++;

	}

	//Handles server stuff
	public static void doReceive(int port, int mtu, int sws) throws IOException, ClassNotFoundException{

		ServerSocket serverSocket = new ServerSocket(port);
		Socket client = serverSocket.accept();
		ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
		ObjectInputStream in = new ObjectInputStream(client.getInputStream());



		while(true){
			Object object;
			tcpPacket packet;
			try{

				object =  in.readObject();

				packet = (tcpPacket) object;

				packetsRecieved++; 


				dataTransferred+=packet.getData().length;

				packet.setS(false);
				packet.setF(false);
				packet.setA(true);
				packet.setAcknowledge( packet.getSequence() + 1 );
				out.writeObject(packet);
				printInfo("snd", System.currentTimeMillis(), "A", 0, 0, packet.getAcknowledge());
				bufRecieve.add( packet);

				//if its the last packet initiate final stuff after acking like noramal
				if(packet.getF() == 1){
					//closeConnectionReciever(in, out, serverSocket, client);
					break;
				}
			}catch(Exception e){
				closeConnectionReciever(in, out, serverSocket, client);
				//				System.out.println("some exception in revciever");
				break;
			}
		}


	}

	public static void closeConnectionReciever
	(ObjectInputStream in, ObjectOutputStream out, ServerSocket serverSocket, Socket socket)
			throws IOException, ClassNotFoundException
	{
		try{
			//Make sure the ack is set properly here
			tcpPacket lastPack = new tcpPacket(seq, ack+1, System.nanoTime(), 0, (short)0, (short)0, "".getBytes());
			lastPack.setF(true);
			lastPack.setA(true);
			out.writeObject(lastPack);
			printInfo("snd", System.currentTimeMillis(), "FA", seq, 0, ack+1);

			//wait for ack
			tcpPacket lastAck = (tcpPacket) in.readObject();
			printInfo("rcv", System.currentTimeMillis(), "A", lastAck.getSequence(), 0, lastPack.getAcknowledge());
		}
		catch(Exception e){

		}finally{

			for(int i = 0; i < bufRecieve.size(); i++){
				for(int j = 0; j < bufRecieve.size(); j++){
					if(i==j)
						continue;
					if(bufRecieve.get(i).getSequence() == bufRecieve.get(j).getSequence()){
						bufRecieve.remove(j);
						numDupAck++;
					}
				}
			}

			packetsDiscarded = (packetsRecieved - numDupAck);

			//sort the packets based on seq num
			Collections.sort(bufRecieve, new Comparator<tcpPacket>() {
				@Override public int compare(tcpPacket p1, tcpPacket p2) {
					return p1.getSequence()- p2.getSequence();
				}
			});

			//checking duplicat		
			//building the file
			String THEFILE = new String();
			String filename = new String();
			filename = filename + (new String(bufRecieve.get(1).getData()));

			for(int i = 2; i < bufRecieve.size(); i++){
				THEFILE = THEFILE+ (new String(bufRecieve.get(i).getData()));
			}	
			//			System.out.println(THEFILE);
			try {
				//				System.out.println(filename);
				File file = new File(filename);
				file.delete();
				FileWriter writer = new FileWriter(file);

				if (file.createNewFile()){
					writer.write(THEFILE);
				}else{
					writer.write(THEFILE);
				}
				writer.close();


			} catch (IOException e) {
				e.printStackTrace();
			}

			//			System.out.print(THEFILE);
			serverSocket.close();
			socket.close();
			System.out.println("Amount of data recieved : " + dataTransferred );
			System.out.println("Number of packets recieved : " + packetsRecieved );
			System.out.println("Number of packets discarded : " + packetsDiscarded);
			System.out.println("Number of retransmissions : " + numRetransmit);
			System.out.println("Number of duplicate acknowledgements : " + 0);			
		}
	}

	public static void closeConnectionSender(ObjectInputStream in, ObjectOutputStream out, Socket socket) throws IOException, ClassNotFoundException{

		try{
			tcpPacket lastPack = new tcpPacket(seq, 0, System.nanoTime(), 0, (short)0, (short)0, "".getBytes());
			lastPack.setF(true);
			out.writeObject(lastPack);
			printInfo("snd", System.currentTimeMillis(), "F", seq, 0, 0);
			packetsSent++;


			//wait for ack from other side
			tcpPacket finAck = (tcpPacket) in.readObject();
			printInfo("rcv", System.currentTimeMillis(), "A", 0, 0, finAck.getAcknowledge());


			//wait for fin from other side
			tcpPacket fin = (tcpPacket) in.readObject();
			printInfo("rcv", System.currentTimeMillis(), "FA", finAck.getSequence(), 0, ack);


			//send the ack with the ack+1
			tcpPacket lastAckPack = new tcpPacket(seq, fin.getAcknowledge()+1, System.nanoTime(), 0, (short)0, (short)0, "".getBytes());
			lastPack.setA(true);
			out.writeObject(lastAckPack);
			printInfo("snd", System.currentTimeMillis(), "A", seq, 0, lastPack.getAcknowledge());
			packetsSent++;
			//now we can close everything and count dupes!
		}catch(Exception e){

		}finally{
			for(int i = 0; i < bufResponse.size(); i++){
				for(int j = 0; j < bufResponse.size(); j++){
					if(i==j)
						continue;
					if(bufResponse.get(i).getSequence() == bufResponse.get(j).getSequence()){
						bufResponse.remove(j);
						numDupAck++;
					}
				}
			}try{
				socket.close();
				in.close();
				out.close();

			}catch(Exception e){

			}finally{
				System.out.println("Amount of data transferred : " + dataTransferred );
				System.out.println("Number of packets sent : " + packetsSent );
				System.out.println("Number of packets discarded : " + packetsDiscarded);
				System.out.println("Number of retransmissions : " + numRetransmit);
				System.out.println("Number of duplicate acknowledgements : " + numDupAck);
			}
		}
	}

	public static void openConnection(ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException{

		//Make and send first packet
		//not sure what to set other than time and SYN
		tcpPacket firstPack = new tcpPacket(seq++, 0, System.nanoTime(), 0, (short)0, (short)0, "".getBytes());
		firstPack.setS(true);

		//You can literally just write objects its nice
		//		out.writeObject(firstPack);
		out.writeObject(firstPack);
		numUnAck++;
		dataTransferred+=firstPack.getData().length;
		packetsSent++;
		//info
		printInfo("snd", System.currentTimeMillis(), "S", seq-1, 0, ack);

		//wait for response
		tcpPacket response = (tcpPacket) in.readObject();

		//we know the response here
		ack = response.getSequence()+1;
		printInfo("rcv", System.currentTimeMillis(), "SA", response.getSequence(), 0, response.getAcknowledge());


		//ack response
		tcpPacket three_way_shake = new tcpPacket(0, ack, System.nanoTime(), 0, (short)0, (short)0, "".getBytes());
		three_way_shake.setA(true);
		//		out.writeObject(three_way_shake);

		out.writeObject(three_way_shake);
		numUnAck++;
		dataTransferred+=firstPack.getData().length;
		packetsSent++;
		printInfo("snd", System.currentTimeMillis(), "A", seq, 0, ack);

	}

	public static void computeTimeout(int s,long t,long c){
		double a = 0.875;
		double b = 0.75;
		int sdev;
		int srtt;

		//Made edev and ertt gloabls
		//I made a global timeout in terms of seconds, use that to calculate timeout
		if(s == 0){
			ertt = (int) (c-t);
			edev = 0;
			timeout = 2*ertt;
		}
		else{
			srtt =(int)( c-t );
			sdev = Math.abs(srtt-ertt);
			ertt = (int) (a*ertt + (1-a)*srtt);
			edev = (int) (b*edev +(1-b)*sdev);
			timeout = ertt + 4*edev;
		}

	}
	public static int checksum(tcpPacket packet){

		int checksum = 0;


		return checksum;
	}

	public static void resend(tcpPacket packet, ObjectOutputStream out) throws IOException{
		out.flush();
		out.writeObject(packet);
		//		numUnAck++;
		dataTransferred+=packet.getData().length;
		packetsSent++;
		//		bufTransfer.add(packet);
		//		System.out.println("resend");
		printInfo("snd", System.currentTimeMillis(), "D", packet.getSequence(), packet.getData().length, packet.getAcknowledge());
		out.flush();
	}

	public static void handleFin(ObjectInputStream in, ObjectOutputStream out, tcpPacket packet){

	}

	public static void handleSyn(ObjectInputStream in, ObjectOutputStream out, tcpPacket packet) throws IOException{
		//get next ack number
		packet.setA(true);
		packet.setAcknowledge(seq++);
		packet.setSequence(0);
		//You can literally just write obejcts its nice
		out.writeObject(packet);
		printInfo("snd", System.currentTimeMillis(), "SA", 0, 0, packet.getAcknowledge());
	}

	public static void printInfo(String type, Long time, String flag, int seqnum, int bytes, int acknum){

		time = time / 1000;

		if(flag.equals("A")){
			System.out.println(type + " " + time + " - A - - " + seqnum + " " +  bytes + " " + acknum);

		}
		else if(flag.equals("S")){
			System.out.println(type + " " + time + " S - - - " + seqnum + " " +  bytes + " " + acknum);

		}
		else if(flag.equals("FA")){
			System.out.println(type + " " + time + " - A F - " + seqnum + " " +  bytes + " " + acknum);

		}
		else if (flag.equals("SA")){
			System.out.println(type + " " + time + " S A - - " + seqnum + " " +  bytes + " " + acknum);

		}
		else if(flag.equals("F")){
			System.out.println(type + " " + time + " - - F - " + seqnum + " " +  bytes + " " + acknum);

		}
		else if(flag.equals("D")){
			System.out.println(type + " " + time + " - - - D " + seqnum + " " +  bytes + " " + acknum);

		}	
	}
	public static byte [] fileToByteArray(String fileName){

		File file = new File(fileName);
		//		byte [] nameOfFile = fileName.getBytes();
		byte[] b = new byte[((int) file.length())];//+file.getName().length()];

		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			//			ByteArrayInputStream byteStream = new ByteArrayInputStream(nameOfFile);

			//adds filename to begining of byte array
			//			byteStream.read(b);

			//adds file to byte array
			fileInputStream.read(b);


		} catch (FileNotFoundException e) {
			System.out.println("File Not Found.");
			e.printStackTrace();
		}
		catch (IOException e1) {
			System.out.println("Error Reading The File.");
			e1.printStackTrace();
		}
		return b;
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException  {


		//Variables that will hold locations of flags in args array
		int portFlag = -1;
		int remotePortFlag = -1;
		int remoteIpFlag = -1;
		int fileFlag = -1;
		int mtuFlag = -1;
		int swsFlag = -1;

		//Variables that will hold actual values
		String remoteIP = "";
		int remotePort = 0;
		String file = "";
		int port = 0;
		int mtu = 0;
		int sws = 0;


		//Iterating arguments to determine flag locations so we can see whats good w them
		for(int i = 0; i < args.length; i++){
			if (args[i].equals("-p")) portFlag = i;
			else if (args[i].equals("-a")) remotePortFlag = i;
			else if (args[i].equals("-s")) remoteIpFlag = i;
			else if (args[i].equals("-f")) fileFlag = i;
			else if (args[i].equals("-m")) mtuFlag = i;
			else if (args[i].equals("-c")) swsFlag = i;
		}

		//setting up client arguments
		if(args.length >7){

			file  = args[fileFlag+1];
			remoteIP = args[remoteIpFlag+1];
			remotePort = Integer.parseInt(args[remotePortFlag+1]);
		}

		//Whether server or client set up the port
		port = Integer.parseInt(args[portFlag +1]);
		mtu = Integer.parseInt(args[mtuFlag +1]);
		sws = Integer.parseInt(args[swsFlag +1]);
		max_transmit_unit = mtu;
		slideWindow = sws;

		if(args.length > 7) doTransfer(file, port, remoteIP, remotePort);
		else doReceive(port, mtu, sws);
	}

}

class tcpPacket implements Serializable{
	protected int sequence;
	protected int acknowledge;
	protected long timeStamp;
	protected int lengthAndFlags;
	protected short allZeros;
	protected short checksum;
	protected byte [] data;

	//Empty constructor for deserialize
	public tcpPacket(){

	}

	public tcpPacket(int seq, int ack, long time, int lenAndFlag, short zero, short checksum, byte [] data){

		this.sequence = seq;
		this.acknowledge = ack;
		this.timeStamp = time;
		this.lengthAndFlags = lenAndFlag;
		this.allZeros = zero;
		this.checksum = checksum;
		this.data = data;


	}

	public int getSequence() {
		return this.sequence;
	}
	public void setSequence(int seq) {
		this.sequence = seq;
	}

	public short getChecksum() {
		return this.checksum;
	}
	public void setChecksum(short checksum) {
		this.checksum = checksum;
	}

	public int getAcknowledge() {
		return this.acknowledge;
	}
	public void setAcknowledge(int ack) {
		this.acknowledge = ack;
	}

	public byte [] getData() {
		return this.data;
	}	 
	public void setData(byte [] data) {
		this.data = data;
	}

	public long getTimeStamp() {
		return this.timeStamp;
	}	 
	public void setTimeStamp(long time) {
		this.timeStamp = time;

	}

	public int getLength() {
		int length = this.lengthAndFlags & 0Xfffffff1;
		return length;
	}	 
	public void setLength(int length) {
		//make sure lower bits 0-28 are 1
		this.lengthAndFlags = (this.lengthAndFlags | 0Xfffffff1) & length ;
	}

	public int getA() {
		int a = this.lengthAndFlags << 31;
		return a;
	}	 
	public void setA(boolean a) {
		//make sure lower bits 0-28 are 1
		if(a)
			this.lengthAndFlags = this.lengthAndFlags | 0X00000008;
		else
			this.lengthAndFlags = this.lengthAndFlags & 0Xfffffff7;
	}
	public int getF() {
		int a = (this.lengthAndFlags & 0x00000004)<< 30;
		return a;
	}	 
	public void setF(boolean f) {
		//make sure lower bits 0-28 are 1
		if(f)
			this.lengthAndFlags = this.lengthAndFlags | 0X00000004;
		else
			this.lengthAndFlags = this.lengthAndFlags & 0xfffffffb;

	}

	public int getS() {
		int a = (this.lengthAndFlags & 0x00000002)<< 29;
		return a;
	}	 
	public void setS(boolean s) {
		//make sure lower bits 0-28 are 1
		if(s)
			this.lengthAndFlags = this.lengthAndFlags | 0X00000002;
		else
			this.lengthAndFlags = this.lengthAndFlags & 0xfffffffd;
	}

	public short getAllZeros() {
		return this.allZeros;
	}	 
	public void setAllZeros(short zero) {
		this.allZeros = zero;
	}

	public String toString(){
		return this.sequence+"";
	}


	//TURNS OUT WE DONT NEED THIS ;)	

	//	public byte[] serialize () throws IOException{
	//		
	//		byte [] data = new byte[24+this.data.length];
	//		
	//		ByteBuffer bb = ByteBuffer.wrap(data);
	//		bb.putShort(allZeros);
	//		bb.putShort(checksum);
	//		bb.putInt(sequence);
	//		bb.putInt(acknowledge);
	//		bb.putInt(lengthAndFlags);
	//		bb.putLong(timeStamp);
	//		bb.put(this.data, 0, this.data.length);
	//		
	//		return data;		
	//	}
	//
	//	public void deserialize (byte[]data){
	//		ByteBuffer bb = ByteBuffer.wrap(data);
	//		this.allZeros = bb.getShort();
	//		this.checksum = bb.getShort();
	//		this.sequence = bb.getInt();
	//		this.acknowledge = bb.getInt();
	//		this.lengthAndFlags = bb.getInt();
	//		this.timeStamp = bb.getLong();
	//		this.data = new byte [((byte)data.length)-bb.position()];
	//		bb.get(this.data, 0, data.length-bb.position());
	//	}

	public void printPacket(){

		System.out.println("ack: " + this.acknowledge);
		System.out.println("zeros: " + this.allZeros);
		System.out.println("checksum: " + this.checksum);
		System.out.println("dataOffset: " + Arrays.toString(this.data));
		System.out.println("len & flags: " + this.lengthAndFlags);
		System.out.println("timestamp: " + this.timeStamp);
		System.out.println("seq: " + this.sequence);

	}
}

