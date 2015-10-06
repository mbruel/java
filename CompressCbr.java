import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.DirectoryStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * CompressCbr allows to compress all the pictures within a folder.
 * Syntax: java CompressCbr 'folder containing the images'
 *
 * The output will be in a folder compressed_'nameOfOriginalFolder'
 * it will contain all the pictures compressed in jpeg
 * if some compression failed, it will copy it into the output folder
 * (This is done for CBR or CBZ compression)
 *
 * In order to compress a CBR or a CBZ that is too big
 * - extract it in a folder (unrar or unzip)
 * - run java CompressCbr on it
 * - check the quality
 * - rar or zip the output folder to make a CBR or CBZ
 *
 *
 * Example:
 * mb@Tieum-Latitude:~/tmp/compress_cbr$ java CompressCbr test
 * Folder name: test, Destination folder: compress_test
 * There are 7 images to compress
 * - test/Antares - T6 - 001.jpg
 * - test/Antares - T6 - 002.jpg
 * - test/Antares - T6 - 000.jpg
 * - test/Antares - T6 - 004.jpg
 * - test/Antares - T6 - 003.jpg
 * - test/fakeImage.png
 * java.lang.IllegalArgumentException: image == null!
 *     at javax.imageio.IIOImage.<init>(IIOImage.java:109)
 *     at CompressCbr.compressImage(CompressCbr.java:226)
 *     at CompressCbr.doCompression(CompressCbr.java:154)
 *     at CompressCbr.main(CompressCbr.java:272)
 * - test/Antares - T6 - 005.jpg
 *
 * 1 images couldn't be compressed, let's copy the original ones...
 * - test/fakeImage.png
 *
 * Number of compressed images: 6
 * Number of images that were copied (compression failed): 1
 * mb@Tieum-Latitude:~/tmp/compress_cbr$
 */
public class CompressCbr {

//	public static boolean debug = true;
	public static boolean debug = false;

	/** Quality JPEG compression factor */
	public static float iJpgCompressionFactor = 0.5f;

	/** Regex for to get the path, image name and extension*/
	public static String sImageExtensions = "^(.*)/([^/]*)\\.(Jpg|jpeg|png|gif)$";

	/** Folder containing the images (extracted cbr or cbz) */
	private String iFolderName;

	/** Destination folder for the compressed images */
	private String iFolderDest;

	/** Path of the Folder containing the images  */
	private Path iPath;

	/** List of the images inside the Folder */
	private List<Path> iImagePathList;

	/** Pattern used to match the extensions of the images */
	private Pattern iFileExtPattern;

	/** List of the images that couldn't be compressed
	 * (we may need to copy the original one into the destination folder
	 *  so if we create a new cbr no pages will be missing)
	 **/
	private List<String> iImagesNotCompressed;



	public CompressCbr(String aFolderName){
		iFolderName = aFolderName;
		iFolderDest = "compress_"+aFolderName;
		println("Folder name: "+iFolderName+", Destination folder: "+iFolderDest);
	}


	public boolean checkFolder(){
		try {
			iPath = Paths.get(iFolderName);
		} catch (InvalidPathException e){
			println("InvalidPathException...");
			return false;
		}

		if (!Files.exists(iPath)) {
			println("the folder doesn't exist");
			return false;
		}

		iFileExtPattern = Pattern.compile(sImageExtensions, Pattern.CASE_INSENSITIVE);

		iImagePathList = new ArrayList<>();
		try{
			DirectoryStream<Path> stream;
			stream = Files.newDirectoryStream(iPath);
			for (Path path : stream) {
				String fileName = path.toString();

				Matcher m = iFileExtPattern.matcher(fileName);
				if (m.matches()){
					debug(m.group(3)+" | "+m.group(2));
					iImagePathList.add(path);
				}
			}
			stream.close();

		} catch (IOException e){
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void printFileList(){
		println("There are "+iImagePathList.size()+" files in the folder");
		for (Path path : iImagePathList){
			println(path.toString());
		}
	}

	/** @return the number of images that have been successfully compressed */
	public int doCompression(){

		int nbCompressed = 0;

		// create destination folder or do nothing if it exists already
		Path destFolder = Paths.get(iFolderDest);
		if (Files.exists(destFolder)) {
			exit(1, "The destination folder already exists... "+iFolderDest);
		} else {
			try {
				Files.createDirectory(destFolder);
			} catch (IOException e) {
				println("Issue creating destination folder...");
				System.err.println(e);
				return nbCompressed;
			}
		}

		iImagesNotCompressed = new ArrayList<String>();
		println("There are "+iImagePathList.size()+" images to compress");
		for (Path path : iImagePathList){
			String fileName = path.toString();
			println("- " + fileName);
			try{
				compressImage(fileName);
				++nbCompressed;
			} catch(Exception e){
				e.printStackTrace();
				iImagesNotCompressed.add(fileName);
			}
		}

		if (iImagesNotCompressed.size()>0){
			println("\n\n"+iImagesNotCompressed.size()+" images couldn't be compressed, let's copy the original ones...");
			for (String image : iImagesNotCompressed){
				println("- "+image);
				Matcher m = iFileExtPattern.matcher(image);
				String destImagePath = "";
				if (m.matches()){
					destImagePath = iFolderDest+"/"+m.group(2)+".jpg";
					try{
						Files.copy(Paths.get(image), Paths.get(destImagePath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e){
						println("Error copying '"+image+"' to '"+destImagePath+"'");
						e.printStackTrace();
					}
				}
			}
		}

		return nbCompressed;
	}



	public void compressImage(String aImagePath) throws IOException {

		Matcher m = iFileExtPattern.matcher(aImagePath);
		String destImagePath = "";
		if (m.matches()){
			destImagePath = iFolderDest+"/"+m.group(2)+".jpg";
			debug("destination: "+destImagePath);
		}

		File imageFile = new File(aImagePath);
		File compressedImageFile = new File(destImagePath);

		InputStream is = new FileInputStream(imageFile);
		OutputStream os = new FileOutputStream(compressedImageFile);


		// create a BufferedImage as the result of decoding the supplied InputStream
		BufferedImage image = ImageIO.read(is);

		// get all image writers for JPG format
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

		if (!writers.hasNext())
			throw new IllegalStateException("No writers found");

		ImageWriter writer = (ImageWriter) writers.next();
		ImageOutputStream ios = ImageIO.createImageOutputStream(os);
		writer.setOutput(ios);

		ImageWriteParam param = writer.getDefaultWriteParam();

		// compress to a given quality (png doesn't support it)
		// http://stackoverflow.com/questions/24538455/compression-of-png-using-jai-java-advanced-imaging
		if (param.canWriteCompressed()) {
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(iJpgCompressionFactor);
		}


		// appends a complete image stream containing a single image and
	    //associated stream and image metadata and thumbnails to the output
		writer.write(null, new IIOImage(image, null, null), param);

		// close all streams
		is.close();
		os.close();
		ios.close();
		writer.dispose();

	}

	public int getNumberOfCompressionFailure(){
		return iImagesNotCompressed.size();
	}

	public static void exit(int value, String reason){
		System.err.println("Exit with reason: "+reason);
		System.exit(value);
	}

	public static void println(String line){
		System.out.println(line);
	}

	public static void debug(String line){
		if (debug){
			println(line);
		}
	}



	public static void main(String[] args){
		debug("Number of Args: "+args.length );
		if (args.length != 1) {
			exit(1, "Please enter the folder name where the images are");
		}

		CompressCbr cbr = new CompressCbr(args[0]);
		if (!cbr.checkFolder()){
			exit(1, "cbr.checkFolder");
		}

		if (debug){
			cbr.printFileList();
		}

		int nbCompressed = cbr.doCompression();
		println("\n\nNumber of compressed images: "+ nbCompressed);
		int nbFailed = cbr.getNumberOfCompressionFailure();
		if (nbFailed > 0){
			println("Number of images that were copied (compression failed): "+ nbFailed);
		}
	}
}
