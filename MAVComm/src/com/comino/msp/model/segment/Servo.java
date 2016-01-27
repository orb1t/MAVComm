package com.comino.msp.model.segment;

import com.comino.msp.model.segment.generic.Segment;

public class Servo extends Segment {

	private static final long serialVersionUID = 6155451845240484694L;
	
	public short 	s0 = 0;
	public short 	s1 = 0;
	public short 	s2 = 0;
	public short 	s3 = 0;
	public short 	s4 = 0;
	public short 	s5 = 0;
	public short 	s6 = 0;
	public short 	s7 = 0;

	public void set(Servo a) {
		s0		= a.s0;
		s1		= a.s1;
		s2		= a.s2;
		s3		= a.s3;
		s4		= a.s4;
		s5		= a.s5;
		s6		= a.s6;
		s7		= a.s7;
		
	}

	public Servo clone() {
		Servo at = new Servo();
		at.s0 		= s0;
		at.s1 		= s1;
		at.s2 		= s2;
		at.s3 		= s3;
		at.s4 		= s4;
		at.s5 		= s5;
		at.s6 		= s6;
		at.s7 		= s7;
		
		return at;
	}

	//--------------------------------------------------------------------------------------------------------


	public void clear() {
		s0 = 0;
		s1 = 0;
		s2 = 0;
		s3 = 0;
		s4 = 0;
		s5 = 0;
		s6 = 0;
		s7 = 0;
	}

}