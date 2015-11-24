package adf.sample.fire.tactics;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.platoon.action.Action;
import adf.agent.platoon.action.common.ActionMove;
import adf.agent.platoon.action.common.ActionRest;
import adf.agent.platoon.action.fire.ActionExtinguish;
import adf.algorithm.path.PathPlanner;
import adf.algorithm.path.SamplePathPlanner;
import adf.sample.fire.tactics.extaction.ActionFireFighting;
import adf.tactics.TacticsFire;
import adf.util.datastorage.DataStorage;
import org.omg.PortableInterceptor.ACTIVE;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import sample.DistanceSorter;

import java.util.*;

import static rescuecore2.misc.Handy.objectsToIDs;

/**
 * Created by takamin on 10/14/15.
 */
public class MyTacticsFire extends TacticsFire
{
	private static final String MAX_WATER_KEY = "fire.tank.maximum";
	private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
	private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";

	private int maxWater;
	private int maxDistance;
	private int maxPower;

	private static final int RANDOM_WALK_LENGTH = 50;

	protected List<EntityID> buildingIDs;
	protected List<EntityID> roadIDs;
	protected List<EntityID> refugeIDs;

	private Map<EntityID, Set<EntityID>> neighbours;
	private Random random;

	private PathPlanner pathPlanner;

	@Override
	public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
		this.random = new Random();
		buildingIDs = new ArrayList<>();
		roadIDs = new ArrayList<>();
		refugeIDs = new ArrayList<>();
	}

	@Override
	public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, DataStorage dataStorage)
	{
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
			if (neighbours.get(current) == null)
			{
				return result;
			}
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
		preparate(agentInfo, worldInfo, scenarioInfo);
	}

	@Override
	public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
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
		//pathplanner
		//neighbours = search.getGraph();
		this.init(worldInfo);
		this.pathPlanner = new SamplePathPlanner(worldInfo, agentInfo, scenarioInfo);
		maxWater = agentInfo.config.getIntValue(MAX_WATER_KEY);
		maxDistance = agentInfo.config.getIntValue(MAX_DISTANCE_KEY);
		maxPower = agentInfo.config.getIntValue(MAX_POWER_KEY);
	}

	public FireBrigade me(WorldInfo worldInfo, AgentInfo agentInfo) {
		return (FireBrigade)worldInfo.world.getEntity(agentInfo.getID());
	}

	@Override
	public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
		FireBrigade me = me(worldInfo, agentInfo);
		// Are we currently filling with water?
		if (me.isWaterDefined() && me.getWater() < maxWater && agentInfo.getLocation() instanceof Refuge) {
			//Logger.info("Filling with water at " + location());
			//sendRest(time);
			return new ActionRest();
		}
		// Are we out of water?
		if (me.isWaterDefined() && me.getWater() == 0) {
			// Head for a refuge
			//List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
			this.pathPlanner.setFrom(agentInfo.getPosition());
			this.pathPlanner.setDist(refugeIDs);
			List<EntityID> path = this.pathPlanner.getResult();
			if (path != null) {
				//Logger.info("Moving to refuge");
				//sendMove(time, path);
				return new ActionMove(path);
			}
			else {
				//Logger.debug("Couldn't plan a path to a refuge.");
				path = randomWalk(agentInfo);
				//Logger.info("Moving randomly");
				//sendMove(time, path);
				//return;
				return new ActionMove(path);
			}
		}
		// Find all buildings that are on fire
		Collection<EntityID> all = getBurningBuildings(worldInfo, agentInfo);
		// Can we extinguish any right now?
		List<Action> results = new ArrayList<>();
		for (EntityID next : all) {
			ActionFireFighting aff = new ActionFireFighting(next, worldInfo, agentInfo, this.pathPlanner);
			Action action = aff.calc().getAction();
			if(action != null) {
				//return action;
				results.add(action);
			}
		}
		////////////////////////////////////////////////////
		for(Action action : results) {
			if(action instanceof ActionExtinguish) {
				return action;
			}
		}
		if(results.size() != 0) {
			//distance1 < distance2
			return results.get(0);
		}
		/////////////////////////////////////////////////////

		List<EntityID> path;
		//Logger.debug("Couldn't plan a path to a fire.");
		path = randomWalk(agentInfo);
		//Logger.info("Moving randomly");
		//sendMove(time, path);
		return new ActionMove(path);
	}

	private Collection<EntityID> getBurningBuildings(WorldInfo worldInfo, AgentInfo agentInfo) {
		Collection<StandardEntity> e = worldInfo.world.getEntitiesOfType(StandardEntityURN.BUILDING);
		List<Building> result = new ArrayList<>();
		for (StandardEntity next : e) {
			if (next instanceof Building) {
				Building b = (Building)next;
				if (b.isOnFire()) {
					result.add(b);
				}
			}
		}
		// Sort by distance
		Collections.sort(result, new DistanceSorter(worldInfo.world.getEntity(agentInfo.getPosition()), worldInfo.world));
		return objectsToIDs(result);
	}

	private List<EntityID> planPathToFire(EntityID target, WorldInfo worldInfo, AgentInfo agentInfo) {
		// Try to get to anything within maxDistance of the target
		Collection<StandardEntity> targets = worldInfo.world.getObjectsInRange(target, maxDistance);
		if (targets.isEmpty()) {
			return null;
		}
		//return search.breadthFirstSearch(this.agentInfo.getPosition(), objectsToIDs(targets));
		this.pathPlanner.setFrom(agentInfo.getPosition());
		this.pathPlanner.setDist(objectsToIDs(targets));
		return this.pathPlanner.getResult();
		//return new ActionMove(path);
	}
}
