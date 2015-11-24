package adf.sample.police.tactics;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.platoon.action.Action;
import adf.agent.platoon.action.common.ActionMove;
import adf.agent.platoon.action.police.ActionClear;
import adf.algorithm.path.PathPlanner;
import adf.algorithm.path.SamplePathPlanner;
import adf.tactics.TacticsPolice;
import adf.util.datastorage.DataStorage;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * Created by takamin on 10/14/15.
 */
public class MyTacticsPolice extends TacticsPolice
{
	private static final String DISTANCE_KEY = "clear.repair.distance";

	private int distance;

	private static final int RANDOM_WALK_LENGTH = 50;

	protected List<EntityID> buildingIDs;
	protected List<EntityID> roadIDs;
	protected List<EntityID> refugeIDs;

	private Map<EntityID, Set<EntityID>> neighbours;
	private Random random;

	private PathPlanner pathPlanner;

	@Override
	public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
		this.random = new Random();
		buildingIDs = new ArrayList<>();
		roadIDs = new ArrayList<>();
		refugeIDs = new ArrayList<>();
	}

	@Override
	public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, DataStorage dataStorage)
	{
		//non precompute
		for (StandardEntity next : worldInfo.world) {
			if (next instanceof Building) {
				buildingIDs.add(next.getID());
			}
			if (next instanceof Road) {
				roadIDs.add(next.getID());
			}
			if (next instanceof Refuge) {
				refugeIDs.add(next.getID());
			}
		}
		this.init(worldInfo);
		this.pathPlanner = new SamplePathPlanner(worldInfo, agentInfo, scenarioInfo);

		distance = scenarioInfo.config.getIntValue(DISTANCE_KEY);
	}

	private void init(WorldInfo worldInfo) {
		Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
			@Override
			public Set<EntityID> createValue() {
				return new HashSet<>();
			}
		};
		Set<EntityID> buildingSet= new HashSet<>();
		for (Entity next : worldInfo.world) {
			if (next instanceof Area) {
				Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
				neighbours.get(next.getID()).addAll(areaNeighbours);
				if (next instanceof Building)
					buildingSet.add(next.getID());
			}
		}
		this.neighbours = neighbours;
	}

	protected List<EntityID> randomWalk(AgentInfo agentInfo) {
		List<EntityID> result = new ArrayList<>(RANDOM_WALK_LENGTH);
		Set<EntityID> seen = new HashSet<>();
		EntityID current = agentInfo.getPosition();
		for (int i = 0; i < RANDOM_WALK_LENGTH; ++i) {
			result.add(current);
			seen.add(current);
			List<EntityID> possible = new ArrayList<>(neighbours.get(current));
			Collections.shuffle(possible, random);
			boolean found = false;
			for (EntityID next : possible) {
				if (seen.contains(next)) {
					continue;
				}
				current = next;
				found = true;
				break;
			}
			if (!found) {
				// We reached a dead-end.
				break;
			}
		}
		return result;
	}

	@Override
	public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, DataStorage dataStorage)
	{

	}

	@Override
	public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{

	}

	@Override
	public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
		Blockade target = getTargetBlockade(worldInfo, agentInfo);
		if (target != null) {
			//Logger.info("Clearing blockade " + target);
			//sendSpeak(time, 1, ("Clearing " + target).getBytes());
//            sendClear(time, target.getX(), target.getY());
			List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
			double best = Double.MAX_VALUE;
			Point2D bestPoint = null;
			Point2D origin = new Point2D(me(worldInfo, agentInfo).getX(), me(worldInfo, agentInfo).getY());
			for (Line2D next : lines) {
				Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
				double d = GeometryTools2D.getDistance(origin, closest);
				if (d < best) {
					best = d;
					bestPoint = closest;
				}
			}
			Vector2D v = bestPoint.minus(new Point2D(me(worldInfo, agentInfo).getX(), me(worldInfo, agentInfo).getY()));
			v = v.normalised().scale(1000000);
			//sendClear(time, );
			return new ActionClear((int)(me(worldInfo, agentInfo).getX() + v.getX()), (int)(me(worldInfo, agentInfo).getY() + v.getY()));
		}
		// Plan a path to a blocked area
		//List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getBlockedRoads());
		this.pathPlanner.setFrom(agentInfo.getPosition());
		((SamplePathPlanner)this.pathPlanner).setDist(getBlockedRoads(worldInfo));
		List<EntityID> path = this.pathPlanner.getResult();
		if (path != null) {
			//Logger.info("Moving to target");
			Road r = (Road)worldInfo.world.getEntity(path.get(path.size() - 1));
			Blockade b = getTargetBlockade(r, -1, worldInfo, agentInfo);
			//sendMove(time, path,
			//Logger.debug("Path: " + path);
			//Logger.debug("Target coordinates: " + b.getX() + ", " + b.getY());
			return new ActionMove(path, b.getX(), b.getY());
		}
		//Logger.debug("Couldn't plan a path to a blocked road");
		//Logger.info("Moving randomly");
		return new ActionMove(randomWalk(agentInfo));
	}

	private List<EntityID> getBlockedRoads(WorldInfo worldInfo) {
		Collection<StandardEntity> e = worldInfo.world.getEntitiesOfType(StandardEntityURN.ROAD);
		List<EntityID> result = new ArrayList<>();
		for (StandardEntity next : e) {
			Road r = (Road)next;
			if (r.isBlockadesDefined() && !r.getBlockades().isEmpty()) {
				result.add(r.getID());
			}
		}
		return result;
	}

	private Blockade getTargetBlockade(WorldInfo worldInfo, AgentInfo agentInfo) {
		//Logger.debug("Looking for target blockade");
		Area location = agentInfo.getLocation();
		//Logger.debug("Looking in current location");
		Blockade result = getTargetBlockade(location, distance, worldInfo, agentInfo);
		if (result != null) {
			return result;
		}
		//Logger.debug("Looking in neighbouring locations");
		for (EntityID next : location.getNeighbours()) {
			location = (Area)worldInfo.world.getEntity(next);
			result = getTargetBlockade(location, distance, worldInfo, agentInfo);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private Blockade getTargetBlockade(Area area, int maxDistance, WorldInfo worldInfo, AgentInfo agentInfo) {
		//        Logger.debug("Looking for nearest blockade in " + area);
		if (area == null || !area.isBlockadesDefined()) {
			//            Logger.debug("Blockades undefined");
			return null;
		}
		List<EntityID> ids = area.getBlockades();
		// Find the first blockade that is in range.
		int x = me(worldInfo, agentInfo).getX();
		int y = me(worldInfo, agentInfo).getY();
		for (EntityID next : ids) {
			Blockade b = (Blockade)worldInfo.world.getEntity(next);
			double d = findDistanceTo(b, x, y);
			//            Logger.debug("Distance to " + b + " = " + d);
			if (maxDistance < 0 || d < maxDistance) {
				//                Logger.debug("In range");
				return b;
			}
		}
		//        Logger.debug("No blockades in range");
		return null;
	}

	private int findDistanceTo(Blockade b, int x, int y) {
		//        Logger.debug("Finding distance to " + b + " from " + x + ", " + y);
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point2D origin = new Point2D(x, y);
		for (Line2D next : lines) {
			Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
			double d = GeometryTools2D.getDistance(origin, closest);
			//            Logger.debug("Next line: " + next + ", closest point: " + closest + ", distance: " + d);
			if (d < best) {
				best = d;
				//                Logger.debug("New best distance");
			}

		}
		return (int)best;
	}

	public PoliceForce me(WorldInfo worldInfo, AgentInfo agentInfo) {
		return (PoliceForce)worldInfo.world.getEntity(agentInfo.getID());
	}
}
