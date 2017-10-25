package nachos.vm;

import nachos.machine.*;
import java.util.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		frames = new TranslationEntry[Machine.processor().getNumPhysPages()];
		PSize = Processor.pageSize;
		swapFile = ThreadedKernel.fileSystem.open("swp", true);
		swapLock = new Lock();
		freeSwaps = new LinkedList<Integer>();
		vpnTospn = new int[Machine.processor().getNumPhysPages()];
		spns = new processContainer[Machine.processor().getNumPhysPages()];
		for(int i = 0; i < spns.length; i++)
		{
			spns[i] = new processContainer();
			spns[i].ppn = -1;
		}
		unpinnedPage = new Condition(new Lock());
		pinLock = new Lock();
		allpinLock = new Lock();
		maxSwapNum = 0;
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
		swapFile.close();
		ThreadedKernel.fileSystem.remove("swp");
	}
	
	public static void swapIn(int swapNum, int pageNum) {
		int swapPos = swapNum * PSize;
		int memPos = pageNum * PSize;

		if(swapNum < 0 || pageNum < 0)
			return;
		
		swapFile.read(swapPos, Machine.processor().getMemory(), 
					  memPos, PSize);

		freeSwaps.add(swapNum);
	}

	public static int swapOut(int pageNum) {
		int swapPos;
		int memPos = pageNum * PSize;

		if(pageNum < 0)
			return -1;
		
		swapLock.acquire();
		if(!freeSwaps.isEmpty())
			swapPos = freeSwaps.removeFirst() * PSize;
		else {
			swapPos = maxSwapNum * PSize;
			maxSwapNum++;
		}

		int writeResult = swapFile.write(swapPos, Machine.processor().getMemory(), 
				   memPos, PSize);
		if(writeResult != -1) {
			swapLock.release();
			return swapPos/PSize;
		}
		
		swapLock.release();		
		return -1;
	}

	public static int clockRep()
	{
		allpinLock.acquire();
		for(int i = 0; i < spns.length; i++)
		{
			if(spns[i].isPin == false) {
				allpin = false;
				break;
			}
			allpin = true;
		}
		
		if(allpin)
			unpinnedPage.sleep();
		allpinLock.release();
		
		while(frames[victim].used == true)
		{
			if(spns[victim].isPin == false)
			{
				frames[victim].used = false;
			}
			victim = (victim + 1) % frames.length;
		}
		
		int toEvict = victim;
		victim = (victim + 1) % frames.length;
		
		TranslationEntry tempTE = frames[toEvict];
		if(tempTE.dirty && !tempTE.readOnly)
		{
			VMProcess tempPro = spns[toEvict].currPro;
			int vpn = tempTE.vpn;
			int swapOutResult = swapOut(toEvict);
			tempPro.vpnTospn[vpn] = swapOutResult;
		}
		
		tempTE.valid = false;
		
		return toEvict;
	}
	
	public static int PPA()
	{
		if(!fpp.isEmpty())
		{
			return (int)fpp.removeFirst();
		}
		else
		{
			return clockRep();
		}
	}
	
	public class processContainer
	{
		public int ppn;
		public VMProcess currPro;
		public boolean isPin;
	}
	
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
	
	private static int victim = 0;
	
	public static TranslationEntry[] frames;
	
	public static processContainer[] spns;
	
	public static Condition unpinnedPage;
	
	public static boolean allpin = false;
	
	public static Lock pinLock;
	
	public static Lock allpinLock;
	
	private static int maxSwapNum;
	
	private static Lock swapLock;
	private static int PSize;
	private static OpenFile swapFile;
	public static LinkedList<Integer> freeSwaps;
	public static int[] vpnTospn;
}
