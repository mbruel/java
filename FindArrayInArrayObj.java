/**
 * FindArrayInArrayObj has a static function getPosition to find the position of an array in another array
 */

public class FindArrayInArrayObj{

	/** Give the position of array2 in array1*/
	public static int getPosition(Object[] array1, Object[] array2){

		int n1 = array1.length, n2 = array2.length;
		int num = n1-n2;

		//if second array is bigger it can not be in the first one
		if (num < 0)
			return -1;

		for (int pos1 = 0; pos1 <= num; ++pos1){
			if (arrayEquals(array1, pos1, array2, n2) )
					return pos1;
		}

		return -1;
	}


	/** return if array1[pos1...pos1+size2-1] is equal to array2
	 *  assert array1.length > size2
	 */
	private static boolean arrayEquals(Object[] array1, int pos1, Object[] array2, int size2){
		for (int i=0; i < size2; ++i){
			if (!array1[pos1+i].equals(array2[i]))
				return false;
		}
		return true;
	}

	/** Readable output of the function FindArrayInArray.getPosition*/
	public static void test(Object[] arr1, Object[] arr2){
		System.out.print("Is ");
		print(arr1);
		System.out.print(" containing ");
		print(arr2);
		System.out.print(" ? ");

		int res = getPosition(arr1, arr2);
		if (res < 0)
			System.out.println("No");
		else
			System.out.println("Yes, in position: "+res);
	}

	/** print an array */
	private static void print(Object[] arr1){
		System.out.print("[");
		for (int i=0; i<arr1.length; ++i){
			if (i!=0)
				System.out.print(",");
			System.out.print(arr1[i]);
		}
		System.out.print("]");
	}


	/** Testing of FindArrayInArray.getPosition with the expected result*/
	public static boolean test_assert(Object[] arr1, Object[] arr2, int result){
		return getPosition(arr1, arr2) == result;
	}


	/** Some Manual Testing */
	public static void main(String[] args){

		test(new Integer[]{2, 3, 4, 5}, new Integer[]{4, 5});
		test(new Integer[]{2, 3, 4, 5}, new Integer[]{2});
		test(new Integer[]{2, 3, 4, 5}, new Integer[]{5});
		test(new Integer[]{2, 3, 4, 5}, new Integer[]{2, 3, 4, 5});
		test(new Integer[]{2, 3, 4, 5}, new Integer[]{2, 3, 4, 5, 6});
		test(new Integer[]{2, 3, 4, 5}, new Integer[]{0, 0});

		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{4, 5}, 2) );
		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{2}, 0) );
		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{5}, 3) );
		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{2, 3, 4, 5}, 0) );
		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{2, 3, 4, 5, 6}, -1) );
		System.out.println( test_assert(new Integer[]{2, 3, 4, 5}, new Integer[]{0, 0}, -1) );

		int pos = FindArrayInArrayObj.getPosition(
				new String[]{"aa", "bb", "cc", "dd"},
				new String[]{"dd"}
		);
		System.out.println(pos);

	}
}
