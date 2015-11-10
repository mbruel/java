import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;


// Only used to send a file
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.Charset;

/**
 * Server that listen on port defined as member
 *
 * It uses a ServerSocketChannel and SocketChannel
 * it only writes Bytes.
 *
 * (Reading from a file, we read in ASCII and write them on the SocketChannel)
 *
 * Three different behaviour to change manually
 *   - sendMessage
 *   - sendSortMessage
 *   - sendFile (static file)
 *
 * (As a client, either use Telnet or SockChan)
 */
public class ServSock {

	static final int    port       = 1111;


	static final byte   CR         = (byte) '\r';          // carriage return on 1 ASCII byte
	static final byte   LF         = (byte) '\n';          // line feed
	static final byte[] endLine    = {CR, LF};             // end line
	static final byte[] endMessage = {(byte) '.', CR, LF}; // endMessage


	public final static String fileToSend = "/home/mb/Documents/svn/swww/notes.html";
	public final static String charsetEncoding = "ISO-8859-15";

	public static void sendMessage(SocketChannel client) throws Exception{
			byte[] line1 = {'H', 'e','l', 'l', 'o', ' ','m', 'a','t', 'e', '!', CR, LF};
			byte[] line2 = {'-', '>', 'P', 'i', 'n', 'g', CR, LF};
			byte[] line3 = {'<', '-','p', 'o', 'n', 'g', '!', CR, LF};
			byte[] line4 = {'y', 'e', 'a', 'a', 'a', 'a','h', '!','!','!', CR, LF};
			byte[] line5 = {'.', CR, LF};

			client.write(ByteBuffer.wrap(line1));
			client.write(ByteBuffer.wrap(line2));
			client.write(ByteBuffer.wrap(line3));
			client.write(ByteBuffer.wrap(line4));
			client.write(ByteBuffer.wrap(line5));
	}

	public static void sendSortMessage(SocketChannel client) throws Exception{
			byte[] line1 = {'H', 'e', 'l', 'l', 'o', CR, LF};
			byte[] line2 = {'m', 'a', 't', 'e', CR, LF};
			byte[] line3 = {'W', 'h','a', 's', 'u', 'p', '?', CR, LF};
			byte[] line4 = {'.', CR, LF};

			client.write(ByteBuffer.wrap(line1));
			client.write(ByteBuffer.wrap(line2));
			client.write(ByteBuffer.wrap(line3));
			client.write(ByteBuffer.wrap(line4));
	}

	public static void sendFile(SocketChannel client) throws Exception{
		File fileDir = new File(fileToSend);

		BufferedReader in = new BufferedReader(
				new InputStreamReader(
					new FileInputStream(fileDir), charsetEncoding));

		for(String line; (line = in.readLine()) != null; ) {
			client.write(ByteBuffer.wrap(line.getBytes(Charset.forName(charsetEncoding))));
			client.write(ByteBuffer.wrap(endLine));
		}
		in.close();

		// Ending message as in NNTP
		client.write(ByteBuffer.wrap(endMessage));
	}

	public static void main(String[] args) throws Exception{
		// create socket channel
		ServerSocketChannel srv = ServerSocketChannel.open();

		// bind channel to port 9001
		srv.socket().bind(new java.net.InetSocketAddress(port));

		for (;;){
			// make connection
			SocketChannel client = srv.accept();

//			sendSortMessage(client);
//			sendMessage(client);
			sendFile(client);


			// close connection
			client.close();
		}
  }
}
