/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.rg.java.jrandoIGN;

/**
 *
 * @author grosbois
 */
public interface TrackModificationBroadcaster {
  public void addTrackModificationListener(TrackModificationListener gsl);

  public void removeTrackModificationListener(TrackModificationListener gsl) ;
  
}
