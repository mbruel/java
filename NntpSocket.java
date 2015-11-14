import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.lang.IllegalArgumentException;

import java.nio.ByteBuffer;
import java.io.PrintStream;

/**
 * NntpSocket implements method for the NNTP protocol unsing SocketASCII.
 *
 * - All lines must end by CRLF (\r\n)
 * - Every command sent to the server would respond with at least one line:
 *       [2-5][0-9][0-9] response message
 *   A success is indicated with the first digit being 2
 *
 * - A multi-lines response would end by this line: .\r\n
 *
 * @author Matthieu Bruel
 * @version 1.0
 *
 * All rights reserved. Relased under terms of the
 * Creative Commons' Attribution-NonCommercial-ShareAlike license.
 */
public class NntpSocket extends SocketASCII {

	/** Display the requests sent to the server */
	protected static boolean          isPrintCmd    = true;

	/** Every command sent to the Server should end with \r\n */
	protected static final byte[]     endOfLine     = { CR, LF };

	/** */
	protected static final ByteBuffer endOfLineBuf  = ByteBuffer.wrap(endOfLine);

	/** Ending of a multi-line Message*/
	protected static final byte       endMessage    = (byte) '.';

	/** success response byte (first byte of the first line of any response from an Nntp Server) */
	protected static final byte       successByte   = (byte) '2';    // First Digit of an answer is 2 if success


	/** Contructor
	 *
	 * @param aBufferSize buffer size (should be bigger than the longest line)
	 */
	public NntpSocket(int aBufferSize){
		super(aBufferSize);
	}

	/** Contructor
	 *
	 * @param aBufferSize buffer size (should be bigger than the longest line)
	 * @param aCharsetEnc charset used by getLine to convert the buffer into a String
	 */
	public NntpSocket(int aBufferSize, String aCharsetEnc){
		super(aBufferSize, aCharsetEnc);
	}


	/** Open the socket channel
	 *  Connect to the host
	 *  Read the Welcome Message from the Newsgroup Server
	 *
	 * @param outputStream Stream to display to response (System.out, socket, file...)
	 * @param host         hostname
	 * @param port         port to connect
	 * @return is the socket channel open and the the Server happy?
	 */
	public boolean connect(PrintStream outputStream, String host, int port){
		boolean err = super.connect(host, port);
		if (!err ){
			debug("NntpSocket::connect", "Couldn't connect to host: "+host+":"+port);
			return false;
		}

		initRead();

		try {
			// We read the Welcome Message
			err = readLine();
			if (!err){
				debug("NntpSocket::connect", "No Welcome Message read");
				return false;
			}

			// We forward it on the output Stream
			outputStream.write(buffer.array(), lineStart, lineEnd-lineStart);
		} catch (IOException | IllegalArgumentException e){
			debug("NntpSocket::connect", "Error Reading Welcome message", e);
			return false;
		}

		// We return if the welcome message is successful (should start with 200)
		return (buffer.array()[lineStart] == successByte);
	}


	/** Do the Authentication handshake
	 *
	 * @param outputStream Stream to display to response (System.out, socket, file...)
	 * @param user         username
	 * @param pass         password
	 * @return if the authentication was successful
	 */
	public boolean doAuthentication(PrintStream outputStream, String user, String pass){
		try {
			doSingleLineCmd("authinfo user "+user, outputStream);
			doSingleLineCmd("authinfo pass "+pass, outputStream);
		} catch (IOException|IllegalArgumentException e){
			debug("NntpSocket::doAuthentication", "Error during authentication", e);
			return false;
		}

		// 281 Welcome to NG_LinK - Enjoy unlimited Usenet downloads!
		return (buffer.array()[lineStart] == successByte);
	}


	/** Do a command with a multi-line response
	 *  - If the first byte of the first line of the response is not a 2
	 *    then there is an error with that command
	 *    there won't be more lines in the response
	 *  - Otherwise the response is finished when we read this line: .\r\n
	 *
	 * @param cmd          Command to send to the server
	 * @param outputStream Stream to display to response (System.out, socket, file...)
	 * @return number of line of the answer
	 */
	public int doMultiLineCmd(String cmd, PrintStream outputStream) throws IOException, IllegalArgumentException {
		// Write the command on the socket channel
		ByteBuffer outBuff = ByteBuffer.wrap(cmd.getBytes(charsetEnc));

		int nbWritten = 0;
		nbWritten+=write(outBuff);

		endOfLineBuf.clear();
		nbWritten+=write(endOfLineBuf);

		printCmd(cmd);

		initRead();
		do{
			boolean ok = readLine();
			if (ok){
				outputStream.write(buffer.array(), lineStart, lineEnd-lineStart);
				++lineNumber;
			}

			if ( (lineNumber == 1) && (buffer.array()[lineStart] != successByte) ){
				break;
			}
		} while ( (lineEnd-lineStart != 3) || (buffer.array()[lineStart] != endMessage) );

		return lineNumber;
	}


	/** Do a command with a single response
	 *  The first byte of the response should be 2 if success
	 *
	 * @param cmd          Command to send to the server
	 * @param outputStream Stream to display to response (System.out, socket, file...)
	 * @return if the request was successful
	 */
	public boolean doSingleLineCmd(String cmd, PrintStream outputStream) throws IOException, IllegalArgumentException {

		// Write the command on the socket channel
		ByteBuffer outBuff = ByteBuffer.wrap(cmd.getBytes(charsetEnc));

		int nbWritten = 0;
		nbWritten+=write(outBuff);

		endOfLineBuf.clear();
		nbWritten+=write(endOfLineBuf);


		printCmd(cmd);

		initRead();
		boolean ok = readLine();
		if (ok){
			outputStream.write(buffer.array(), lineStart, lineEnd-lineStart);
		}

		return (buffer.array()[lineStart] == endMessage);
	}


	/** Close the socket properly by sending first the quit message
	 *
	 * @param outputStream Stream to display to response (System.out, socket, file...)
	 */
	public void close(PrintStream outputStream){
		// send the QUIT message
		try {
			doSingleLineCmd("quit", outputStream);
		} catch (IOException|IllegalArgumentException e){
			debug("NntpSocket::close", "Error sending quit message", e);
		}

		// closing the channel socket
		super.close();
	}


	/** Print a request
	 *
	 * @param cmd request to print
	 */
	public void printCmd(String cmd){
		if (isPrintCmd){
			System.out.println("REQ> "+cmd);
		}
	}



	/** Example on how to use it*/
	public static void main(String args[]) throws Exception {
		SocketASCII.isDebug    = false;
		SocketASCII.dispBuffer = false;
		NntpSocket.isPrintCmd  = true;

		String host = "localhost";
		int    port = 119;
		int    size = 1024;
		String user = "myUsername";
		String pass = "myPassword";

		// create the socket
		NntpSocket sock = new NntpSocket(size, defaultCharset);

		// Try to connect to the Server
		boolean connected = sock.connect(System.out, host, port);
		if (!connected){
			sock.debug("NntpSocket::main", "Error connecting to the socket");
			sock.close();
			return;
		}

		// Try to authenticate with the server
		if (! sock.doAuthentication(System.out, user, pass) ){
			sock.debug("NntpSocket::main", "Error during Authentication");
			sock.close();
			return;
		}


		// GROUP cmd (only one line answer)
		String newsgroup="alt.binaries.ebook";
		try {
			sock.doSingleLineCmd("group "+newsgroup, System.out);
		} catch (IOException|IllegalArgumentException e){
			sock.debug("NntpSocket::main", "Error group command", e);
			exit(sock);
			return;
		}

		// HEAD cmd (multi lines answer)
		try {
			sock.doMultiLineCmd("head 30663536", System.out);
		} catch (IOException|IllegalArgumentException e){
			sock.debug("NntpSocket::main", "Error group command", e);
			exit(sock);
			return;
		}

		// Close the socket
		exit(sock);
	}

	/** Close an NntpSocket and diplay how many bytes were read
	 *
	 * @param sock the NntpSocket to close
	 */
	public static void exit(NntpSocket sock){
		sock.close(System.out);
		sock.debug("NntpSocket::exit", "[end] Total number of Bytes read: "+sock.getTotalBytesRead());
	}

}
