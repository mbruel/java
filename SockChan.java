import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.lang.StringBuilder;
import java.io.IOException;



/**
 * Use SocketChannel with a ByteBuffer associated to a byte[]
 *
 * Read on the socket and expects lines of ASCII text (bytes) terminated by \r\n
 * To indicate the end of transmission, the last line should be: .\r\n
 *
 * It can be used with ServSock that would either send a text file or a message
 *
 * readLine: fetch the lines in the buffer without touching them and write them on the output
 * no string conversion, only using Bytes.
 * Potentially we could get any of them as a String and process them (cf the commented block)
 * For the NNTP case, only the first line would be relevant
 *
 */
public class SockChan{

	static final String host             = "localhost";
	static final int port                = 1111;


	public static boolean isDebug        = false;
	public static boolean dispBuffer     = false;

	static final int bufferSize          = 1024;          // for webpage
//	static final int bufferSize          = 16;            // for small message
//
	public final static String charsetEnc= "ISO-8859-15"; // used to create a String from the ASCII bytes

	static final byte   CR               = (byte) '\r';   // carriage return on 1 ASCII byte
	static final byte   LF               = (byte) '\n';   // line feed
	static final byte[] endLine          = {CR, LF};      // end line
	static final byte   endMessage       = (byte) '.';    // endMessage


	public static boolean isEndMessage   = false;


	public static byte[] bytesArray;           // actual array buffer
	public static ByteBuffer buffer;           // wraps bytesArray

	// Could use buffer.mark() and buffer.reset()
	// but a bit more confusing and we can't access the marked value
	public static int startPos           = 0;

	public static int totalBytesNumber   = 0;  // get the download size



	public static int readLine() throws IOException {
		// remember the position of the start of the line
		startPos = buffer.position();

		byte l = 0;
		while (buffer.hasRemaining()) {
			byte b = buffer.get();
			if (b == LF && l == CR) {
				return buffer.position();
			}
			l = b;
		}
		return -1;
	}


	public static void main(String args[]) throws Exception {
		SocketChannel channel = null;

		bytesArray = new byte[bufferSize];
		buffer = ByteBuffer.wrap(bytesArray);

		InetSocketAddress socketAddress = new InetSocketAddress(host, port);
		channel = SocketChannel.open();
		channel.connect(socketAddress);


		int byteReads = 0;
		while( (byteReads = channel.read(buffer)) > 0){
			totalBytesNumber += byteReads;


			buffer.flip();


			debug("\n\nbyteReads: "+byteReads);
			writeBufState();


			int endOfLine;
			do {
				startPos = 0;
				endOfLine = readLine();
				// Current line is between startPos and endOfLine
				// current position is at endOfLine


				if (endOfLine == -1) {
					// no more to read on the buffer
					break;
				}

				// End of message: .\r\n
				// (not need to read anymore on the socket)
				else if ( (endOfLine-startPos == 3)
						&&  (buffer.array()[startPos] == endMessage) )
				{
					isEndMessage = true;
					debug("[isEndMessage Received]");
					break;
				}

				// We have a line
				else {
					// Let's print the line
					System.out.write(buffer.array(), startPos, endOfLine-startPos);
/*
					{
						// Create a String from the line and do something with it
						String line = new String(buffer.array(), startPos, endOfLine-startPos, charsetEnc);
						System.out.print(line);
					}
*/
				}
			} while ( (endOfLine != -1) && !isEndMessage); // End readLine

			// End of message received, we stop reading on the socket
			if (isEndMessage) {
				break;
			}

			// If the buffer didn't finish on an end of line
			// we compact it at it is not circular...
			if (startPos != buffer.limit()){
				// set the position to the end of the last line
				buffer.position(startPos);

				// Compact buffer
				// - copy the data after the position at the beginning
				// - reset pos accordingly at the number of element that have been copied
				buffer.compact();

			} else {
				// we finished the buffer by an end of line
				// we just set the position to 0
				buffer.position(0);
			}

		} // end channel.read


		System.out.println("[end] Total number of Bytes read: "+totalBytesNumber);
		channel.close();
	}



	public static void debug(String str){
		if (isDebug){
			System.out.println(str);
		}
	}


	public static void writeBufState() throws Exception{
		if (isDebug){
			System.out.print("[Buffer]: pos="+buffer.position()
					+", lim=" + buffer.limit()
					+", startPos=" + startPos
					+", buff: ");
		}
		if (dispBuffer){
			System.out.write(buffer.array());
		} else if (isDebug){
			System.out.write(endLine);
		}
	}

}
