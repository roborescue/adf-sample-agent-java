package adf.challenge.module.algorithm.clustering;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.util.WorldUtil;
import rescuecore2.misc.Pair;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;

public class PathBasedKMeans extends Clustering {

    public static final String KEY_ALL_ELEMENTS = "sample.clustering.entities";
    public static final String KEY_CLUSTER_SIZE = "sample.clustering.size";
    public static final String KEY_CLUSTER_CENTER = "sample.clustering.centers";
    public static final String KEY_CLUSTER_ENTITY = "sample.clustering.entities.";
    public static final String KEY_ASSIGN_AGENT = "sample.clustering.assign";
    private Collection<StandardEntity> entities;

    protected List<StandardEntity> centerList;
    protected List<List<StandardEntity>> clusterEntityList;

    private boolean assignAgentsFlag;

    private int repeat = 10;

    private int clusterSize;

    private Map<EntityID, Set<EntityID>> shortestPathGraph;

    public PathBasedKMeans(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        this(ai, wi, si, moduleManager, 10, true, wi.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        ));
    }

    protected PathBasedKMeans(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, int size, boolean assignAgentsFlag, Collection<StandardEntity> entities) {
        super(ai, wi, si, moduleManager);
        this.clusterSize = size;
        this.assignAgentsFlag = assignAgentsFlag;
        this.entities = entities;
    }

    @Override
    public Clustering precompute(PrecomputeData precomputeData) {
        precomputeData.setEntityIDList(KEY_ALL_ELEMENTS, (List<EntityID>)WorldUtil.convertToID(this.entities));
        precomputeData.setInteger(KEY_CLUSTER_SIZE, this.clusterSize);
        precomputeData.setEntityIDList(KEY_CLUSTER_CENTER, (List<EntityID>)WorldUtil.convertToID(this.centerList));
        for(int i = 0; i < this.clusterSize; i++) {
            precomputeData.setEntityIDList(KEY_CLUSTER_ENTITY + i, (List<EntityID>)WorldUtil.convertToID(this.clusterEntityList.get(i)));
        }
        precomputeData.setBoolean(KEY_ASSIGN_AGENT, this.assignAgentsFlag);
        return this;
    }

    @Override
    public Clustering resume(PrecomputeData precomputeData) {
        this.entities = WorldUtil.convertToEntity(precomputeData.getEntityIDList(KEY_ALL_ELEMENTS), this.worldInfo);
        this.clusterSize = precomputeData.getInteger(KEY_CLUSTER_SIZE);
        this.centerList = new ArrayList<>(WorldUtil.convertToEntity(precomputeData.getEntityIDList(KEY_CLUSTER_CENTER), this.worldInfo));
        this.clusterEntityList = new ArrayList<>(this.clusterSize);
        for(int i = 0; i < this.clusterSize; i++) {
            List<StandardEntity> list = new ArrayList<>(WorldUtil.convertToEntity(precomputeData.getEntityIDList(KEY_CLUSTER_ENTITY + i), this.worldInfo));
            this.clusterEntityList.add(i, list);
        }
        this.clusterEntityList.sort(comparing(List::size, reverseOrder()));
        this.assignAgentsFlag = precomputeData.getBoolean(KEY_ASSIGN_AGENT);
        return this;
    }

    @Override
    public Clustering preparate() {
        return this;
    }

    @Override
    public int getClusterNumber() {
        return 0;
    }

    @Override
    public int getClusterIndex(StandardEntity entity) {
        for(int i = 0; i < this.clusterSize; i++) {
            if(this.clusterEntityList.get(i).contains(entity)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getClusterIndex(EntityID id) {
        return this.getClusterIndex(this.worldInfo.getEntity(id));
    }

    @Override
    public Collection<StandardEntity> getClusterEntities(int index) {
        return this.clusterEntityList.get(index);
    }

    @Override
    public Collection<EntityID> getClusterEntityIDs(int index) {
        return WorldUtil.convertToID(this.getClusterEntities(index));
    }

    @Override
    public Clustering calc() {
        this.initShortestPath(this.worldInfo);
        Random random = new Random();

        List<StandardEntity> entityList = new ArrayList<>(this.entities);
        this.centerList = new ArrayList<>(this.clusterSize);
        this.clusterEntityList = new ArrayList<>(this.clusterSize);

        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntityList.add(index, new ArrayList<>());
            this.centerList.add(index, entityList.get(0));
        }

        for (int index = 0; index < this.clusterSize; index++) {
            StandardEntity centerEntity;
            do {
                centerEntity = entityList.get(Math.abs(random.nextInt()) % entityList.size());
            } while (this.centerList.contains(centerEntity));
            this.centerList.set(index, centerEntity);
        }

        for (int i = 0; i < this.repeat; i++) {
            this.clusterEntityList.clear();
            for (int index = 0; index < this.clusterSize; index++) {
                this.clusterEntityList.add(index, new ArrayList<>());
            }
            for (StandardEntity entity : entityList) {
                StandardEntity tmp = this.getNearEntity(this.worldInfo, this.centerList, entity);
                this.clusterEntityList.get(this.centerList.indexOf(tmp)).add(entity);
            }

            for (int index = 0; index < this.clusterSize; index++) {
                int sumX = 0, sumY = 0;
                for (StandardEntity entity : this.clusterEntityList.get(index)) {
                    Pair<Integer, Integer> location = entity.getLocation(this.worldInfo.getRawWorld());
                    sumX += location.first();
                    sumY += location.second();
                }
                int centerX = sumX / clusterEntityList.get(index).size();
                int centerY = sumY / clusterEntityList.get(index).size();

                //this.centerList.set(index, getNearEntity(this.worldInfo, this.clusterEntityList.get(index), centerX, centerY));
                StandardEntity center = this.getNearEntity(this.worldInfo, this.clusterEntityList.get(index), centerX, centerY);
                if(center instanceof Area) {
                    this.centerList.set(index, center);
                }
                else if(center instanceof Human) {
                    this.centerList.set(index, this.worldInfo.getEntity(((Human) center).getPosition()));
                }
                else if(center instanceof Blockade) {
                    this.centerList.set(index, this.worldInfo.getEntity(((Blockade) center).getPosition()));
                }
            }
            System.out.print("*");
        }
        System.out.println();

        this.clusterEntityList.clear();
        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntityList.add(index, new ArrayList<>());
        }
        for (StandardEntity entity : entityList) {
            StandardEntity tmp = this.getNearEntity(this.worldInfo, this.centerList, entity);
            this.clusterEntityList.get(this.centerList.indexOf(tmp)).add(entity);
        }

        this.clusterEntityList.sort(comparing(List::size, reverseOrder()));

        if(this.assignAgentsFlag) {
            List<StandardEntity> firebrigadeList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
            List<StandardEntity> policeforceList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE));
            List<StandardEntity> ambulanceteamList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM));

            this.assignAgents(this.worldInfo, firebrigadeList);
            this.assignAgents(this.worldInfo, policeforceList);
            this.assignAgents(this.worldInfo, ambulanceteamList);
        }
        return this;
    }

    private void assignAgents(WorldInfo world, List<StandardEntity> agentList) {
        int clusterIndex = 0;
        while (agentList.size() > 0) {
            StandardEntity center = this.centerList.get(clusterIndex);
            StandardEntity agent = this.getNearAgent(world, agentList, center);
            this.clusterEntityList.get(clusterIndex).add(agent);
            agentList.remove(agent);
            clusterIndex++;
            if (clusterIndex >= this.clusterSize) {
                clusterIndex = 0;
            }
        }
    }

    private StandardEntity getNearAgent(WorldInfo worldInfo, List<StandardEntity> srcAgentList, StandardEntity targetEntity) {
        StandardEntity result = null;
        for (StandardEntity agent : srcAgentList) {
            Human human = (Human)agent;
            if (result == null) {
                result = agent;
            }
            else {
                if (this.comparePathDistance(worldInfo, targetEntity, result, worldInfo.getPosition(human)).equals(worldInfo.getPosition(human))) {
                    result = agent;
                }
            }
        }
        return result;
    }

    private StandardEntity getNearEntity(WorldInfo worldInfo, List<StandardEntity> srcEntityList, int targetX, int targetY) {
        StandardEntity result = null;
        for (StandardEntity entity : srcEntityList) {
            result = (result != null) ? this.compareLineDistance(worldInfo, targetX, targetY, result, entity) : entity;
        }
        return result;
    }

    public Point2D getEdgePoint(Edge edge) {
        Point2D start = edge.getStart();
        Point2D end = edge.getEnd();
        return new Point2D(((start.getX() + end.getX()) / 2.0D), ((start.getY() + end.getY()) / 2.0D));
    }


    public double getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = fromX - toX;
        double dy = fromY - toY;
        return Math.hypot(dx, dy);
    }
    public double getDistance(Pair<Integer, Integer> from, Point2D to) {
        return getDistance(from.first(), from.second(), to.getX(), to.getY());
    }
    public double getDistance(Pair<Integer, Integer> from, Edge to) {
        return getDistance(from, getEdgePoint(to));
    }

    public double getDistance(Point2D from, Point2D to) {
        return getDistance(from.getX(), from.getY(), to.getX(), to.getY());
    }
    public double getDistance(Edge from, Edge to) {
        return getDistance(getEdgePoint(from), getEdgePoint(to));
    }

    private StandardEntity compareLineDistance(WorldInfo worldInfo, int targetX, int targetY, StandardEntity first, StandardEntity second)
    {
        double firstDistance = getDistance(first.getLocation(worldInfo.getRawWorld()).first(), first.getLocation(worldInfo.getRawWorld()).second(), targetX, targetY);
        double secondDistance = getDistance(second.getLocation(worldInfo.getRawWorld()).first(), second.getLocation(worldInfo.getRawWorld()).second(), targetX, targetY);
        return (firstDistance < secondDistance ? first : second);
    }

    private StandardEntity getNearEntity(WorldInfo worldInfo, List<StandardEntity> srcEntityList, StandardEntity targetEntity)
    {
        StandardEntity result = null;
        for (StandardEntity entity : srcEntityList) {
            result = (result != null) ? this.comparePathDistance(worldInfo, targetEntity, result, entity) : entity;
        }
        return result;
    }

    private StandardEntity comparePathDistance(WorldInfo worldInfo, StandardEntity target, StandardEntity first, StandardEntity second) {
        double firstDistance = getPathDistance(worldInfo, shortestPath(target.getID(), first.getID()));
        double secondDistance = getPathDistance(worldInfo, shortestPath(target.getID(), second.getID()));
        return (firstDistance < secondDistance ? first : second);
    }

    private double getPathDistance(WorldInfo worldInfo, List<EntityID> path) {
        double distance = 0.0D;

        if (path == null) {
            return Double.MAX_VALUE;
        }

        int limit = path.size() - 1;

        if(path.size() == 1) {
            return 0.0D;
        }

        Area area = (Area)worldInfo.getEntity(path.get(0));
        distance += getDistance(area.getLocation(worldInfo.getRawWorld()), area.getEdgeTo(path.get(1)));
        area = (Area)worldInfo.getEntity(path.get(limit));
        distance += getDistance(area.getLocation(worldInfo.getRawWorld()), area.getEdgeTo(path.get(limit - 1)));

        EntityID areaID;
        for(int i = 1; i < limit; i++) {
            areaID = path.get(i);
            area = (Area)worldInfo.getEntity(areaID);
            distance += getDistance(area.getEdgeTo(path.get(i - 1)), area.getEdgeTo(path.get(i + 1)));
        }
        return distance;
    }

    private void initShortestPath(WorldInfo worldInfo) {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<>();
            }
        };
        for (Entity next : worldInfo) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
            }
        }
        for (Map.Entry<EntityID, Set<EntityID>> graph : neighbours.entrySet()) {// fix graph
            for (EntityID entityID : graph.getValue()) {
                neighbours.get(entityID).add(graph.getKey());
            }
        }
        this.shortestPathGraph = neighbours;
    }

    private List<EntityID> shortestPath(EntityID start, EntityID... goals) {
        return shortestPath(start, Arrays.asList(goals));
    }

    private List<EntityID> shortestPath(EntityID start, Collection<EntityID> goals) {
        List<EntityID> open = new LinkedList<>();
        Map<EntityID, EntityID> ancestors = new HashMap<>();
        open.add(start);
        EntityID next;
        boolean found = false;
        ancestors.put(start, start);
        do {
            next = open.remove(0);
            if (isGoal(next, goals)) {
                found = true;
                break;
            }
            Collection<EntityID> neighbours = shortestPathGraph.get(next);
            if (neighbours.isEmpty()) {
                continue;
            }
            for (EntityID neighbour : neighbours) {
                if (isGoal(neighbour, goals)) {
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
            return null;
        }
        // Walk back from goal to start
        EntityID current = next;
        List<EntityID> path = new LinkedList<>();
        do {
            path.add(0, current);
            current = ancestors.get(current);
            if (current == null) {
                throw new RuntimeException("Found a node with no ancestor! Something is broken.");
            }
        } while (current != start);
        return path;
    }

    private boolean isGoal(EntityID e, Collection<EntityID> test) {
        return test.contains(e);
    }
}
