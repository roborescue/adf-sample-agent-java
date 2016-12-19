package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingDetector;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleBuildingDetector extends BuildingDetector {
    private EntityID result;

    private Clustering clustering;

    private int sendTime;
    private int sendingAvoidTimeClearRequest;

    private Collection<EntityID> agentPositions;
    private Map<EntityID, Integer> sentTimeMap;
    private int sendingAvoidTimeReceived;
    private int sendingAvoidTimeSent;

    private int moveDistance;
    private EntityID lastPosition;
    private int positionCount;

    public SampleBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        switch  (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
        this.sendTime = 0;
        this.sendingAvoidTimeClearRequest = developData.getInteger("SampleBuildingDetector.sendingAvoidTimeClearRequest", 5);

        this.agentPositions = new HashSet<>();
        this.sentTimeMap = new HashMap<>();
        this.sendingAvoidTimeReceived = developData.getInteger("SampleBuildingDetector.sendingAvoidTimeReceived", 3);
        this.sendingAvoidTimeSent = developData.getInteger("SampleBuildingDetector.sendingAvoidTimeSent", 5);

        this.moveDistance = developData.getInteger("SampleBuildingDetector.moveDistance", 40000);
    }

    @Override
    public BuildingDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.clustering.updateInfo(messageManager);

        this.reflectMessage(messageManager);
        this.sendEntityInfo(messageManager);

        if(this.result != null) {
            Building building = (Building)this.worldInfo.getEntity(this.result);
            if(building.getFieryness() >= 4) {
                messageManager.addMessage(new MessageBuilding(true, building));
            }
        }

        int currentTime = this.agentInfo.getTime();
        Human agent = (Human)this.agentInfo.me();
        int agentX = agent.getX();
        int agentY = agent.getY();
        StandardEntity positionEntity = this.worldInfo.getPosition(agent);
        if(positionEntity instanceof Road) {
            Road road = (Road)positionEntity;
            if(road.isBlockadesDefined() && road.getBlockades().size() > 0) {
                for(Blockade blockade : this.worldInfo.getBlockades(road)) {
                    if(blockade == null || !blockade.isApexesDefined()) {
                        continue;
                    }
                    if(this.isInside(agentX, agentY, blockade.getApexes())) {
                        if ((this.sendTime + this.sendingAvoidTimeClearRequest) <= currentTime) {
                            this.sendTime = currentTime;
                            messageManager.addMessage(
                                    new CommandPolice(
                                            true,
                                            null,
                                            agent.getPosition(),
                                            CommandPolice.ACTION_CLEAR
                                    )
                            );
                            break;
                        }
                    }
                }
            }
            if(this.lastPosition != null && this.lastPosition.getValue() == road.getID().getValue()) {
                this.positionCount++;
                if(this.positionCount > this.getMaxTravelTime(road)) {
                    if ((this.sendTime + this.sendingAvoidTimeClearRequest) <= currentTime) {
                        this.sendTime = currentTime;
                        messageManager.addMessage(
                                new CommandPolice(
                                        true,
                                        null,
                                        agent.getPosition(),
                                        CommandPolice.ACTION_CLEAR
                                )
                        );
                    }
                }
            } else {
                this.lastPosition = road.getID();
                this.positionCount = 0;
            }
        }
        return this;
    }

    @Override
    public BuildingDetector calc() {
        this.result = this.calcTargetInCluster();
        if(this.result == null) {
            this.result = this.calcTargetInWorld();
        }
        return this;
    }

    private EntityID calcTargetInCluster() {
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = this.clustering.getClusterEntities(clusterIndex);
        if(elements == null || elements.isEmpty()) {
            return null;
        }
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : elements) {
            if (entity instanceof Building && ((Building)entity).isOnFire()) {
                fireBuildings.add(entity);
            }
        }
        for(StandardEntity entity : fireBuildings) {
            if(agents.isEmpty()) {
                break;
            } else if(agents.size() == 1) {
                if(agents.get(0).getID().getValue() == me.getID().getValue()) {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(this.worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if(me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue()) {
                return entity.getID();
            } else {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    private EntityID calcTargetInWorld() {
        Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : entities) {
            if (((Building)entity).isOnFire()) {
                fireBuildings.add(entity);
            }
        }
        for(StandardEntity entity : fireBuildings) {
            if(agents.isEmpty()) {
                break;
            } else if(agents.size() == 1) {
                if(agents.get(0).getID().getValue() == me.getID().getValue()) {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(this.worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if(me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue()) {
                return entity.getID();
            } else {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public BuildingDetector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.clustering.precompute(precomputeData);
        return this;
    }

    @Override
    public BuildingDetector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.clustering.resume(precomputeData);
        return this;
    }

    @Override
    public BuildingDetector preparate() {
        super.preparate();
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.clustering.preparate();
        return this;
    }

    private boolean isInside(double pX, double pY, int[] apex) {
        Point2D p = new Point2D(pX, pY);
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        double theta = this.getAngle(v1, v2);

        for(int i = 0; i < apex.length - 2; i += 2) {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }

    private double getAngle(Vector2D v1, Vector2D v2) {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if(flag > 0) {
            return angle;
        }
        if(flag < 0) {
            return -1 * angle;
        }
        return 0.0D;
    }

    private class DistanceSorter implements Comparator<StandardEntity> {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference) {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b) {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
        int time = this.agentInfo.getTime();
        for(CommunicationMessage message : messageManager.getReceivedMessageList(MessageBuilding.class)) {
            MessageBuilding mb = (MessageBuilding)message;
            if(!changedEntities.contains(mb.getBuildingID())) {
                MessageUtil.reflectMessage(this.worldInfo, mb);
            }
            this.sentTimeMap.put(mb.getBuildingID(), time + this.sendingAvoidTimeReceived);
        }
    }

    private boolean checkSendFlags(){
        boolean isSendBuildingMessage = true;

        StandardEntity me = this.agentInfo.me();
        if(!(me instanceof Human)){
            return false;
        }
        Human agent = (Human)me;
        EntityID agentID = agent.getID();
        EntityID position = agent.getPosition();
        StandardEntityURN agentURN = agent.getStandardURN();
        EnumSet<StandardEntityURN> agentTypes = EnumSet.of(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE);
        agentTypes.remove(agentURN);

        this.agentPositions.clear();
        for(StandardEntity entity : this.worldInfo.getEntitiesOfType(agentURN)) {
            Human other = (Human)entity;
            if(isSendBuildingMessage) {
                if (other.getPosition().getValue() == position.getValue()) {
                    if (other.getID().getValue() > agentID.getValue()) {
                        isSendBuildingMessage = false;
                    }
                }
            }
            this.agentPositions.add(other.getPosition());
        }
        for(StandardEntityURN urn : agentTypes) {
            for(StandardEntity entity : this.worldInfo.getEntitiesOfType(urn)) {
                Human other = (Human) entity;
                if(isSendBuildingMessage) {
                    if (other.getPosition().getValue() == position.getValue()) {
                        if (urn == FIRE_BRIGADE) {
                            isSendBuildingMessage = false;
                        } else if (agentURN != FIRE_BRIGADE && other.getID().getValue() > agentID.getValue()) {
                            isSendBuildingMessage = false;
                        }
                    }
                }
                this.agentPositions.add(other.getPosition());
            }
        }
        return isSendBuildingMessage;
    }

    private void sendEntityInfo(MessageManager messageManager) {
        if(this.checkSendFlags()) {
            Building building = null;
            int currentTime = this.agentInfo.getTime();
            Human agent = (Human) this.agentInfo.me();
            for (EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
                StandardEntity entity = this.worldInfo.getEntity(id);
                if (entity instanceof Building) {
                    Integer time = this.sentTimeMap.get(id);
                    if (time != null && time > currentTime) {
                        continue;
                    }
                    Building target = (Building) entity;
                    if (!this.agentPositions.contains(target.getID())) {
                        building = this.selectBuilding(building, target);
                    } else if (target.getID().getValue() == agent.getPosition().getValue()) {
                        building = this.selectBuilding(building, target);
                    }
                }
            }
            if (building != null) {
                messageManager.addMessage(new MessageBuilding(true, building));
                this.sentTimeMap.put(building.getID(), currentTime + this.sendingAvoidTimeSent);
            }
        }
    }

    private Building selectBuilding(Building building1, Building building2) {
        if(building1 != null) {
            if(building2 != null) {
                if(building1.isOnFire() && building2.isOnFire()) {
                    if (building1.getFieryness() < building2.getFieryness()) {
                        return building2;
                    } else if (building1.getFieryness() > building2.getFieryness()) {
                        return building1;
                    }
                    if(building1.isTemperatureDefined() && building2.isTemperatureDefined()) {
                        return building1.getTemperature() < building2.getTemperature() ? building2 : building1;
                    }
                } else if (!building1.isOnFire() && building2.isOnFire()) {
                    return building2;
                }
            }
            return building1;
        }
        return building2 != null ? building2 : null;
    }

    private int getMaxTravelTime(Area area) {
        int distance = 0;
        List<Edge> edges = new ArrayList<>();
        for(Edge edge : area.getEdges()) {
            if(edge.isPassable()) {
                edges.add(edge);
            }
        }
        if(edges.size() <= 1) {
            return Integer.MAX_VALUE;
        }
        for(int i = 0; i < edges.size(); i++) {
            for(int j = 0; j < edges.size(); j++) {
                if(i != j) {
                    Edge edge1 = edges.get(i);
                    double midX1 = (edge1.getStartX() + edge1.getEndX()) / 2;
                    double midY1 = (edge1.getStartY() + edge1.getEndY()) / 2;
                    Edge edge2 = edges.get(j);
                    double midX2 = (edge2.getStartX() + edge2.getEndX()) / 2;
                    double midY2 = (edge2.getStartY() + edge2.getEndY()) / 2;
                    int d = this.getDistance(midX1, midY1, midX2, midY2);
                    if(distance < d) {
                        distance = d;
                    }
                }
            }
        }
        if(distance > 0) {
            return (distance / this.moveDistance) + ((distance % this.moveDistance) > 0 ? 1 : 0) + 1;
        }
        return Integer.MAX_VALUE;
    }

    private int getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        return (int)Math.hypot(dx, dy);
    }
}
