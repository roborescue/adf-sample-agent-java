package adf.sample.algorithm.pathplanning;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.pathplanning.PathPlanner;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SamplePathPlanner extends PathPlanner {

    private Map<EntityID, Set<EntityID>> graph;

    private Table<EntityID, EntityID, List<EntityID>> cache;

    private EntityID from;
    private List<EntityID> result;

    public SamplePathPlanner(AgentInfo ai, WorldInfo wi, ScenarioInfo si) {
        super(ai, wi, si);
        this.init();
    }

    private void init() {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<>();
            }
        };
        for (Entity next : this.worldInfo) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
            }
        }
        this.graph = neighbours;
        this.cache = HashBasedTable.create();
    }

    @Override
    public List<EntityID> getResult() {
        return this.result;
    }

    @Override
    public void setFrom(EntityID id) {
        this.from = id;
    }

    @Override
    public PathPlanner setDist(Collection<EntityID> targets) {
        //check cache
        if(this.hasCache(targets)) {
            return this;
        }
        //calc
        List<EntityID> open = new LinkedList<>();
        Map<EntityID, EntityID> ancestors = new HashMap<>();
        open.add(this.from);
        EntityID next;
        boolean found = false;
        ancestors.put(this.from, this.from);
        do {
            next = open.remove(0);
            if (isGoal(next, targets)) {
                found = true;
                break;
            }
            Collection<EntityID> neighbours = graph.get(next);
            if (neighbours.isEmpty()) {
                continue;
            }
            for (EntityID neighbour : neighbours) {
                if (isGoal(neighbour, targets)) {
                    ancestors.put(neighbour, next);
                    next = neighbour;
                    found = true;
                    break;
                }
                else {
                    if (!ancestors.containsKey(neighbour)) {
                        open.add(neighbour);
                        ancestors.put(neighbour, next);
                    }
                }
            }
        } while (!found && !open.isEmpty());
        if (!found) {
            // No path
            this.result = null;
        }
        // Walk back from goal to this.from
        EntityID current = next;
        LinkedList<EntityID> path = new LinkedList<>();
        do {
            path.add(0, current);
            current = ancestors.get(current);
            if (current == null) {
                throw new RuntimeException("Found a node with no ancestor! Something is broken.");
            }
        } while (current != this.from);
        this.result = path;
        this.cache.put(path.getFirst(), path.getLast(), path);
        this.cache.put(path.getLast(), path.getFirst(), path);
        return this;
    }

    private boolean hasCache(Collection<EntityID> targets) {
        for(EntityID next : targets) {
            this.result = this.cache.get(this.from, next);
            if(this.result != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isGoal(EntityID e, Collection<EntityID> test) {
        return test.contains(e);
    }
}
