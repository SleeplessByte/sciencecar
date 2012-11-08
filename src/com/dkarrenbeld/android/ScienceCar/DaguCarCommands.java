package com.dkarrenbeld.android.ScienceCar;

public final class DaguCarCommands {
	
	public static final byte STOP 	= (byte) 0x00;
	public static final byte NORTH 	= (byte) 0x10;
	public static final byte SOUTH 	= (byte) 0x20;
	public static final byte WEST 	= (byte) 0x30;
	public static final byte EAST 	= (byte) 0x40;
	public static final byte NORTHWEST  = (byte) 0x50;
	public static final byte NORTHEAST  = (byte) 0x60;
	public static final byte SOUTHWEST  = (byte) 0x70;
	public static final byte SOUTHHEAST = (byte) 0x80;
	
	//public static final byte STOP = (byte) 0x00;
	public static final byte SLOWEST = (byte) 0x01;
	public static final byte WAY_WAY_SLOW = (byte) 0x02;
	public static final byte WAY_SLOW = (byte) 0x03;
	public static final byte LESS_WAY_SLOW = (byte) 0x04;
	public static final byte SLOW = (byte) 0x05;
	public static final byte EASY_GOING	= (byte) 0x06;
	public static final byte CRUISING = (byte) 0x07;
	public static final byte MOVING_RIGHT_ALONG = (byte) 0x08;
	public static final byte MOVING_QUICK = (byte) 0x09;
	public static final byte MOVING_QUICKER = (byte) 0x0A;
	public static final byte MOVING_PRETTY_DARN_QUICK = (byte) 0x0B;
	public static final byte FAST = (byte) 0x0C;
	public static final byte FASTER = (byte) 0x0D;
	public static final byte FASTEST = (byte) 0x0E;
	public static final byte I_LIED_THIS_IS_FASTEST = (byte) 0x0F;
	
	/**
	 * Creates command bye
	 * @param direction
	 * @param speed
	 * @return
	 */
	public static final byte CreateCommand(byte direction, byte speed)
	{
		return (byte) (direction + speed);
	}
}
