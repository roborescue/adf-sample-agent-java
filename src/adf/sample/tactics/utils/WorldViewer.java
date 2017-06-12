package adf.sample.tactics.utils;

import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import rescuecore2.config.Config;
import rescuecore2.log.CommandsRecord;
import rescuecore2.log.LogException;
import rescuecore2.log.UpdatesRecord;
import rescuecore2.view.EntityInspector;
import rescuecore2.view.RenderedObject;
import rescuecore2.view.ViewComponent;
import rescuecore2.view.ViewListener;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.WorldModel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import static rescuecore2.misc.java.JavaTools.instantiate;

public class WorldViewer extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2732220527053489740L;

	private static final String VIEWERS_KEY = "log.viewers";

	private JLabel timestep;
	private EntityInspector inspector;
	private List<ViewComponent> viewers;

	private WorldInfo worldinfo;

	private ScenarioInfo scenarioInfo;

	/**
	 * Construct a LogViewer.
	 * 
	 * @param reader
	 *            The LogReader to read.
	 * @param config
	 *            The system configuration.
	 * @throws LogException
	 *             If there is a problem reading the log.
	 */
	public WorldViewer(WorldInfo worldinfo, ScenarioInfo scenarioInfo)  {
		super(new BorderLayout());
		this.worldinfo = worldinfo;
		this.scenarioInfo = scenarioInfo;
		inspector = new EntityInspector();
		registerViewers(scenarioInfo.getRawConfig());
		Dictionary<Integer, JComponent> labels = new Hashtable<Integer, JComponent>();
		timestep = new JLabel("Timestep: 0");
		JTabbedPane tabs = new JTabbedPane();
		for (ViewComponent next : viewers) {
			tabs.addTab(next.getViewerName(), next);
			next.addViewListener(new ViewListener() {
				@Override
				public void objectsClicked(ViewComponent view, List<RenderedObject> objects) {
					for (RenderedObject next : objects) {
						if (next.getObject() instanceof Entity) {
							inspector.inspect((Entity) next.getObject());
							return;
						}
					}
				}

				@Override
				public void objectsRollover(ViewComponent view, List<RenderedObject> objects) {
				}
			});
		}
		JSplitPane split1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inspector, tabs);
		add(split1, BorderLayout.CENTER);
		add(timestep, BorderLayout.NORTH);
	}

	/**
	 * Show a particular timestep in the viewer.
	 * 
	 * @param time
	 *            The timestep to show. If this value is out of range then this
	 *            method will silently return.
	 */
	public void showTimestep(int time) {
		timestep.setText("Timestep: " + time);
		CommandsRecord commandsRecord = null;// log.getCommands(time);
		UpdatesRecord updatesRecord = null;// log.getUpdates(time);

		WorldModel<? extends Entity> model = worldinfo.getRawWorld();
		for (ViewComponent next : viewers) {
			next.view(model, commandsRecord == null ? null : commandsRecord.getCommands(), updatesRecord == null ? null : updatesRecord.getChangeSet());
			next.repaint();
		}
	}

	
	private void registerViewers(Config config) {
		viewers = new ArrayList<ViewComponent>();
		Config c2 = new Config(config);
		c2.appendValue("viewer.standard.AreaNeighboursLayer.visible", "false");
		for (String next : config.getArrayValue(VIEWERS_KEY, "rescuecore2.standard.view.AnimatedWorldModelViewer")) {
			
			ViewComponent viewer = instantiate(next, ViewComponent.class);
			if (viewer != null) {
				viewer.initialise(c2);
				viewers.add(viewer);
			}
		}
	}

}
