
package cz.it4i.fiji.parallel_macro.functions;

import java.nio.IntBuffer;

import ij.macro.MacroExtension;
import mpi.MPI;
import mpi.MPIException;

public class Flip implements MyMacroExtensionDescriptor {

	@Override
	public void runFromMacro(Object[] parameters) {
		// Get input:
		String inputString = (String) parameters[0];
		String resultString = (String) parameters[1];
		double flipXString = (double) parameters[2];
		double flipYString = (double) parameters[3];

		// Convert input to proper types:
		boolean flipXBoolean = false;
		boolean flipYBoolean = false;

		// Macro always uses double for numbers and booleans:
		if (flipXString == 1.0) {
			flipXBoolean = true;
		}
		if (flipYString == 1.0) {
			flipYBoolean = true;
		}

		// Call the actual function:
		parallelFlip(inputString, resultString, flipXBoolean, flipYBoolean);
	}

	private void parallelFlip(String input, String result, boolean flipX,
		boolean flipY)
	{
		try {
			// Get rank and size of nodes:
			int rank = MPI.COMM_WORLD.getRank();
			int size = MPI.COMM_WORLD.getSize();

			ImageInputOutput imageInputOutput = new ImageInputOutput();

			System.out.println("Trying to flip the image.");

			// Read the selected image:
			IntBuffer imagePixels = imageInputOutput.readImage(input);
			int width = imageInputOutput.getWidth();
			int height = imageInputOutput.getHeight();

			IntBuffer imagePixels2 = MPI.newIntBuffer(width * height);
			System.out.println("Rank " + rank + ". Done reading image!");

			if (flipY || flipX) {
				int[] heightParts = new int[size];
				int[] displacementHeightParts = new int[size];
				int[] counts = new int[size];
				int[] displacements = new int[size];

				for (int loopRank = 0; loopRank < size; loopRank++) {
					heightParts[loopRank] = height / size;
					displacementHeightParts[loopRank] = loopRank * heightParts[loopRank];
					if (loopRank == size - 1) {
						// The last node should also do any remaining work.
						heightParts[loopRank] += height % size;
					}
					counts[loopRank] = heightParts[loopRank] * width;
					displacements[loopRank] = displacementHeightParts[loopRank] * width;
				}

				// Create smaller local array of the image:
				IntBuffer yFlippedPixels = MPI.newIntBuffer(width * heightParts[rank]);

				// Each node should flip its image part:
				for (int x = 0; x < width; x++) {
					for (int y =
						displacementHeightParts[rank]; y < (displacementHeightParts[rank] +
							heightParts[rank]); y++)
					{
						setValueAt(yFlippedPixels, width, x, (y -
							displacementHeightParts[rank]), getValueAt(imagePixels, width,
								(flipX) ? (width - 1 - x) : x, flipY ? (height - 1) - y : y));
					}
				}

				System.out.println("About to gather!" + rank + " send count: " +
					heightParts[rank] + " displacements: " +
					displacementHeightParts[rank] + " 1D count: " + counts[rank] +
					" 1D displacement " + displacements[rank]);

				// Gather image parts from all nodes together:
				MPI.COMM_WORLD.gatherv(yFlippedPixels, heightParts[rank] * width,
					MPI.INT, imagePixels2, counts, displacements, MPI.INT, 0);
				System.out.println("Gathered! " + rank);
			}
			else {
				imagePixels2 = imagePixels;
			}

			MPI.COMM_WORLD.barrier();
			// Write the selected image:
			if (rank == 0) {
				imageInputOutput.writeImage(result, copyIntBufferToArray(imagePixels2,
					width * height));
				System.out.println("Done writing image!");
			}
		}
		catch (MPIException e) {
			e.printStackTrace();
		}
	}

	private static int[] copyIntBufferToArray(IntBuffer buffer, int bufferSize) {
		int[] array = new int[bufferSize];
		for (int i = 0; i < bufferSize; i++) {
			array[i] = buffer.get(i);
		}
		return array;
	}

	// Do not remove this version. Keep this serial version for testing.
	private void serialFlip(String input, String result, boolean flipX,
		boolean flipY)
	{
		ImageInputOutput imageInputOutput = new ImageInputOutput();

		System.out.println("Trying to flip the image.");

		// Read the selected image:
		IntBuffer imagePixels = imageInputOutput.readImage(input);
		System.out.println("Done reading image!");

		int width = imageInputOutput.getWidth();
		int height = imageInputOutput.getHeight();

		if (flipX) {
			IntBuffer xFlippedPixels = MPI.newIntBuffer(width * height);

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					setValueAt(xFlippedPixels, width, x, y, getValueAt(imagePixels, width,
						(width - 1) - x, y));
				}
			}
			imagePixels = xFlippedPixels;
		}

		if (flipY) {
			IntBuffer yFlippedPixels = MPI.newIntBuffer(width * height);

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					setValueAt(yFlippedPixels, width, x, y, getValueAt(imagePixels, width,
						x, (height - 1) - y));
				}
			}
			imagePixels = yFlippedPixels;
		}

		// Write the selected image:
		imageInputOutput.writeImage(result, copyIntBufferToArray(imagePixels,
			width * height));
		System.out.println("Done writing image!");
	}

	private static void setValueAt(IntBuffer pixels, int width, int x, int y,
		int value)
	{
		pixels.put(x + y * width, value);
	}

	private static int getValueAt(IntBuffer pixels, int width, int x, int y) {
		return pixels.get(x + y * width);
	}

	@Override
	public int[] parameterTypes() {
		return new int[] { MacroExtension.ARG_STRING, MacroExtension.ARG_STRING,
			MacroExtension.ARG_NUMBER, MacroExtension.ARG_NUMBER };
	}

	@Override
	public String description() {
		return "Flips an image in the dimension or dimensions specified.";
	}

	@Override
	public String parameters() {
		return "input, result, flipX, flipY";
	}

}
