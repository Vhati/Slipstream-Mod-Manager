package net.vhati.modmanager.ui;


/*
 * An interface to en/disable user interaction.
 * It was written with JFrames and glass panes in mind.
 */
public interface Nerfable {

	/*
	 * Either nerf or restore user interaction.
	 *
	 * @param b the nerfed state
	 */
	public void setNerfed( boolean b );
}
