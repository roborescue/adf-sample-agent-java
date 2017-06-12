package adf.sample.tactics.utils;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.launcher.annotation.NoStructureWarning;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

@NoStructureWarning
public class WorldViewLauncher {

	private static WorldViewLauncher INSTANCE;
	HashMap<EntityID, WorldViewer> map=new HashMap<>();
	private DefaultListModel<StandardEntity> registeredEntities;
	private JList<StandardEntity> jlist;

	public WorldViewLauncher(){
		JFrame frame=new JFrame("WorldModel Viewer");
		registeredEntities=new DefaultListModel<>();
		jlist=new JList<>(registeredEntities);
		frame.getContentPane().add(new JScrollPane(jlist));
		jlist.addMouseListener(new MouseListener() {

			@Override
			public void mouseReleased(MouseEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mousePressed(MouseEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mouseExited(MouseEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mouseEntered(MouseEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount()==2){
					showWorld(registeredEntities.get(jlist.getSelectedIndex()));
				}
			}
		});

		frame.setSize(300,500);
		frame.setVisible(true);
	}

	public void showTimeStep(AgentInfo agentInfo,WorldInfo worldInfo,ScenarioInfo scenarioInfo){
		if(!map.containsKey(agentInfo.getID())){
			map.put(agentInfo.getID(), new WorldViewer(worldInfo, scenarioInfo));
			registeredEntities.addElement(agentInfo.me());
		}
		map.get(agentInfo.getID()).showTimestep(agentInfo.getTime());
	}
	public void showWorld(StandardEntity standardEntity){
		JFrame frame=new JFrame(standardEntity.toString());
		frame.getContentPane().add(map.get(standardEntity.getID()));
		frame.setExtendedState(frame.getExtendedState()|JFrame.MAXIMIZED_BOTH);
		frame.setSize(600, 480);
		frame.setVisible(true);
		
	}
	
	
	public static synchronized WorldViewLauncher getInstance(){
		if(INSTANCE==null)
			INSTANCE=new WorldViewLauncher();
		return INSTANCE;
	}
}
