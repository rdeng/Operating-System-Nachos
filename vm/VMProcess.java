package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();	
	
		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOff = Processor.offsetFromAddress(vaddr);
		if(vpn < 0 || vpn >= pageTable.length)
			return 0;
		TranslationEntry ent = pageTable[vpn];
		
		//check virtual page is valid
		if(!ent.valid)
			handlePageFault(vpn, vpnOff);
			
		int phyAdd = pageSize*ent.ppn + vpnOff;
		ent.used = true;
		VMKernel.spns[ent.ppn].isPin = true;
		int amount = 0;

		while(length > 0)
		{
			if(vpnOff + length <= pageSize)
			{
				System.arraycopy(memory, phyAdd, data, offset, length);
				amount += length;
				length -= length;
			}
			else
			{
				int written = pageSize - vpnOff;
				length = length - written;
				System.arraycopy(memory, phyAdd, data, offset, written);
				amount += written;
				offset += written;
				//pageTable[vpn].used = false;
				VMKernel.spns[ent.ppn].isPin = false;
				
				VMKernel.pinLock.acquire();
				if(VMKernel.allpin)
				{
					VMKernel.allpin = false;
					VMKernel.unpinnedPage.wake();
				}
				VMKernel.pinLock.release();
				
				vpn++;
				if(vpn >= pageTable.length)
				{
					break;
				}
				else
				{
					ent = pageTable[vpn];
					if(!ent.valid)
						handlePageFault(vpn, vpnOff);
					ent.used = true;
					VMKernel.spns[ent.ppn].isPin = true;
					vpnOff = 0;
					phyAdd = pageSize * ent.ppn + vpnOff;
				}
			}
		}
		
		//pageTable[vpn].used = false;
		VMKernel.spns[ent.ppn].isPin = false;
		
		VMKernel.pinLock.acquire();
		if(VMKernel.allpin)
		{
			VMKernel.allpin = false;
			VMKernel.unpinnedPage.wake();
		}
		VMKernel.pinLock.release();
		
		return amount;
	}
	
	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOff = Processor.offsetFromAddress(vaddr);
		if(vpn < 0 || vpn >= pageTable.length)
			return 0;
		TranslationEntry ent = pageTable[vpn];
		ent.dirty = true;
		
		//check virtual page is valid
		if(!ent.valid)
			handlePageFault(vpn, vpnOff);
		
		int phyAdd = pageSize*ent.ppn + vpnOff;
		ent.used = true;
		VMKernel.spns[ent.ppn].isPin = true;
		int amount = 0;
		
		if(ent.readOnly == true)
			return amount;

		while(length > 0)
		{
			if(vpnOff + length <= pageSize)
			{
				System.arraycopy(data, offset, memory, phyAdd, length);
				amount += length;
				length -= length;
			}
			else
			{
				int written = pageSize - vpnOff;
				length = length - written;
				System.arraycopy(data, offset, memory, phyAdd, written);
				amount += written;
				offset += written;
				//pageTable[vpn].used = false;
				VMKernel.spns[ent.ppn].isPin = false;
				
				VMKernel.pinLock.acquire();
				if(VMKernel.allpin)
				{
					VMKernel.allpin = false;
					VMKernel.unpinnedPage.wake();
				}
				VMKernel.pinLock.release();
				
				vpn++;
				if(vpn >= pageTable.length)
				{
					break;
				}
				else
				{
					ent = pageTable[vpn];
					if(!ent.valid)
						handlePageFault(vpn, vpnOff);
					ent.dirty = true;
					ent.used = true;
					VMKernel.spns[ent.ppn].isPin = false;
					vpnOff = 0;
					phyAdd = pageSize * ent.ppn + vpnOff;
				}
			}
		}
		
		//pageTable[vpn].used = false;
		VMKernel.spns[ent.ppn].isPin = false;
		
		VMKernel.pinLock.acquire();
		if(VMKernel.allpin)
		{
			VMKernel.allpin = false;
			VMKernel.unpinnedPage.wake();
		}
		VMKernel.pinLock.release();
		
		return amount;
	}
	
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		vpnTospn = new int[numPages];
		for(int i = 0; i < numPages; i++)
			vpnTospn[i] = -1;
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
		{
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				VMKernel.pageSem.P();
				TranslationEntry ent = pageTable[vpn];
				ent.vpn = vpn;
				ent.readOnly = section.isReadOnly();
				VMKernel.pageSem.V();
			}
		}
		
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		VMKernel.pageSem.P();
		for(int vpn = 0; vpn < pageTable.length; vpn++)
		{
			if(pageTable[vpn].valid)
			{
				VMKernel.fpp.add((Integer)(pageTable[vpn].ppn));
			}
		}
		VMKernel.pageSem.V();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault:
			int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(vaddr);
			int vpnOff = Processor.offsetFromAddress(vaddr);
			handlePageFault(vpn, vpnOff);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	
	public void handlePageFault(int inputVpn, int vpnOff)
	{
		TranslationEntry ent = pageTable[inputVpn];
		if(ent.dirty && !ent.readOnly)
		{
			ent.ppn = VMKernel.PPA();
			VMKernel.swapIn(vpnTospn[inputVpn], ent.ppn);
			ent.valid = true;
			VMKernel.frames[ent.ppn] = ent;
			VMKernel.spns[ent.ppn].currPro = this;
			VMKernel.spns[ent.ppn].ppn = ent.ppn;
		}
		else
		{
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
	
				Lib.debug(dbgProcess, "\tinitializing " + section.getName()
						+ " section (" + section.getLength() + " pages)");
	
				for (int i = 0; i < section.getLength(); i++) {
					int vpn = section.getFirstVPN() + i;
					
					VMKernel.pageSem.P();
					if(vpn == inputVpn)
					{
						ent.vpn = vpn;
						ent.ppn = VMKernel.PPA();
						VMKernel.frames[ent.ppn] = ent;
						VMKernel.spns[ent.ppn].currPro = this;
						VMKernel.spns[ent.ppn].ppn = ent.ppn;
						ent.valid = true;
						ent.readOnly = section.isReadOnly();
						VMKernel.pageSem.V();
						section.loadPage(i, ent.ppn);
						return;
					}
					VMKernel.pageSem.V();
				}
			}
			
			// not in coff section case, zero-fill
			VMKernel.pageSem.P();
			ent.ppn = VMKernel.PPA();
			VMKernel.frames[ent.ppn] = ent;
			VMKernel.spns[ent.ppn].currPro = this;
			VMKernel.spns[ent.ppn].ppn = ent.ppn;
			byte[] memory = Machine.processor().getMemory();
			byte[] buffer = new byte[pageSize];
			System.arraycopy(buffer, 0, memory, ent.ppn*pageSize, pageSize);
			ent.valid = true;
			VMKernel.pageSem.V();
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
	
	public int[] vpnTospn;
}
