package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanDetector;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleHumanDetector extends HumanDetector {
    private Clustering clustering;

    private EntityID result;

    private int sendTime;
    private int sendingAvoidTimeClearRequest;

    private Collection<EntityID> agentPositions;
    private Map<EntityID, Integer> sentTimeMap;
    private int sendingAvoidTimeReceived;
    private int sendingAvoidTimeSent;

    private int moveDistance;
    private EntityID lastPosition;
    private int positionCount;

    public SampleHumanDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);

        this.result = null;
        this.sendTime = 0;
        this.sendingAvoidTimeClearRequest = developData.getInteger("SampleHumanDetector.sendingAvoidTimeClearRequest", 5);

        this.agentPositions = new HashSet<>();
        this.sentTimeMap = new HashMap<>();
        this.sendingAvoidTimeReceived = developData.getInteger("SampleHumanDetector.sendingAvoidTimeReceived", 3);
        this.sendingAvoidTimeSent = developData.getInteger("SampleHumanDetector.sendingAvoidTimeSent", 5);

        this.moveDistance = developData.getInteger("SampleHumanDetector.moveDistance", 40000);

        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
    }

    @Override
    public HumanDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.clustering.updateInfo(messageManager);

        this.reflectMessage(messageManager);
        this.sendEntityInfo(messageManager);

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

        if(this.result != null) {
            StandardEntity entity = this.worldInfo.getEntity(this.result);
            if(entity != null && entity instanceof Human) {
                Human h = (Human)entity;
                if(h.isPositionDefined()) {
                    StandardEntity humanPosition = this.worldInfo.getPosition(h);
                    if(humanPosition != null) {
                        if (humanPosition instanceof Building && ((Building) humanPosition).isOnFire()) {
                            messageManager.addMessage(
                                    new CommandFire(
                                            true,
                                            null,
                                            humanPosition.getID(),
                                            CommandFire.ACTION_EXTINGUISH
                                    )
                            );
                        } else if (humanPosition.getStandardURN() == AMBULANCE_TEAM && h.getStandardURN() == CIVILIAN) {
                            messageManager.addMessage(
                                    new MessageCivilian(
                                            true,
                                            (Civilian) h
                                    )
                            );
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public HumanDetector calc() {
        Human transportHuman =  this.agentInfo.someoneOnBoard();
        if(transportHuman != null) {
            this.result = transportHuman.getID();
            return this;
        }
        if(this.result != null) {
            Human target = (Human) this.worldInfo.getEntity(this.result);
            if(target != null) {
                if (!target.isHPDefined() || target.getHP() == 0) {
                    this.result = null;
                } else if (!target.isPositionDefined()) {
                    this.result = null;
                } else {
                    StandardEntity position = this.worldInfo.getPosition(target);
                    if(position != null) {
                        StandardEntityURN positionURN = position.getStandardURN();
                        if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM) {
                            this.result = null;
                        }
                    }
                }
            }
        }
        if(this.result == null) {
            if (clustering == null) {
                this.result = this.calcTargetInWorld();
                return this;
            }
            this.result = this.calcTargetInCluster(clustering);
            if (this.result == null) {
                this.result = this.calcTargetInWorld();
            }
        }
        return this;
    }

    private EntityID calcTargetInCluster(Clustering clustering) {
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if(elements == null || elements.isEmpty()) {
            return null;
        }

        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE)) {
            Human h = (Human) next;
            if(this.agentInfo.getID().getValue() == h.getID().getValue()) {
                continue;
            }
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if(positionEntity != null && elements.contains(positionEntity) || elements.contains(h)) {
                if (h.isHPDefined() && h.isBuriednessDefined() && h.getHP() > 0 && h.getBuriedness() > 0) {
                    rescueTargets.add(h);
                }
            }
        }
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN)) {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if(positionEntity != null && positionEntity instanceof Area) {
                if (elements.contains(positionEntity)) {
                    if (h.isHPDefined() && h.getHP() > 0) {
                        if (h.isBuriednessDefined() && h.getBuriedness() > 0) {
                            rescueTargets.add(h);
                        } else {
                            if (h.isDamageDefined() && h.getDamage() > 0 && positionEntity.getStandardURN() != REFUGE) {
                                loadTargets.add(h);
                            }
                        }
                    }
                }
            }
        }
        if(rescueTargets.size() > 0) {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return rescueTargets.get(0).getID();
        }
        if(loadTargets.size() > 0) {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return loadTargets.get(0).getID();
        }
        return null;
    }

    private EntityID calcTargetInWorld() {
        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE)) {
            Human h = (Human) next;
            if (this.agentInfo.getID().getValue() != h.getID().getValue()) {
                StandardEntity positionEntity = this.worldInfo.getPosition(h);
                if (positionEntity != null && h.isHPDefined() && h.isBuriednessDefined()) {
                    if (h.getHP() > 0 && h.getBuriedness() > 0) {
                        rescueTargets.add(h);
                    }
                }
            }
        }
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN)) {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if(positionEntity != null && positionEntity instanceof Area) {
                if (h.isHPDefined() && h.getHP() > 0) {
                    if (h.isBuriednessDefined() && h.getBuriedness() > 0) {
                        rescueTargets.add(h);
                    } else {
                        if (h.isDamageDefined() && h.getDamage() > 0  && positionEntity.getStandardURN() != REFUGE) {
                            loadTargets.add(h);
                        }
                    }
                }
            }
        }
        if(rescueTargets.size() > 0) {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return rescueTargets.get(0).getID();
        }
        if(loadTargets.size() > 0) {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return loadTargets.get(0).getID();
        }
        return null;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public HumanDetector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.clustering.precompute(precomputeData);
        return this;
    }

    @Override
    public HumanDetector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.clustering.resume(precomputeData);
        return this;
    }

    @Override
    public HumanDetector preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.clustering.preparate();
        return this;
    }

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
        int time = this.agentInfo.getTime();
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!changedEntities.contains(mc.getAgentID())){
                    MessageUtil.reflectMessage(this.worldInfo, mc);
                }
                this.sentTimeMap.put(mc.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mat);
                }
                this.sentTimeMap.put(mat.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!changedEntities.contains(mfb.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mfb);
                }
                this.sentTimeMap.put(mfb.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mpf);
                }
                this.sentTimeMap.put(mpf.getAgentID(), time + this.sendingAvoidTimeReceived);
            }
        }
    }

    private boolean checkSendFlags(){
        boolean isSendCivilianMessage = true;

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
            if(isSendCivilianMessage) {
                if (other.getPosition().getValue() == position.getValue()) {
                    if (other.getID().getValue() > agentID.getValue()) {
                        isSendCivilianMessage = false;
                    }
                }
            }
            this.agentPositions.add(other.getPosition());
        }

        for(StandardEntityURN urn : agentTypes) {
            for(StandardEntity entity : this.worldInfo.getEntitiesOfType(urn)) {
                Human other = (Human)entity;
                if(isSendCivilianMessage) {
                    if (other.getPosition().getValue() == position.getValue()) {
                        if (urn == AMBULANCE_TEAM) {
                            isSendCivilianMessage = false;
                        } else if(agentURN != AMBULANCE_TEAM && other.getID().getValue() > agentID.getValue()) {
                            isSendCivilianMessage = false;
                        }
                    }
                }
                this.agentPositions.add(other.getPosition());
            }
        }
        return isSendCivilianMessage;
    }

    private void sendEntityInfo(MessageManager messageManager) {
        if(this.checkSendFlags()) {
            Civilian civilian = null;
            int currentTime = this.agentInfo.getTime();
            Human agent = (Human) this.agentInfo.me();
            for (EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
                StandardEntity entity = Objects.requireNonNull(this.worldInfo.getEntity(id));
                if (entity.getStandardURN() == CIVILIAN) {
                    Integer time = this.sentTimeMap.get(id);
                    if (time != null && time > currentTime) {
                        continue;
                    }
                    Civilian target = (Civilian) entity;
                    if (!this.agentPositions.contains(target.getPosition())) {
                        civilian = this.selectCivilian(civilian, target);
                    } else if (target.getPosition().getValue() == agent.getPosition().getValue()) {
                        civilian = this.selectCivilian(civilian, target);
                    }
                }
            }
            if (civilian != null) {
                messageManager.addMessage(new MessageCivilian(true, civilian));
                this.sentTimeMap.put(civilian.getID(), currentTime + this.sendingAvoidTimeSent);
            }
        }
    }

    private Civilian selectCivilian(Civilian civilian1, Civilian civilian2) {
        if(this.checkCivilian(civilian1, true)) {
            if(this.checkCivilian(civilian2, true)) {
                if(civilian1.getHP() > civilian2.getHP()) {
                    return civilian1;
                } else if(civilian1.getHP() < civilian2.getHP()) {
                    return civilian2;
                }else {
                    if(civilian1.isBuriednessDefined() && civilian2.isBuriednessDefined()) {
                        if(civilian1.getBuriedness() > 0 && civilian2.getBuriedness() == 0) {
                            return civilian1;
                        }else if(civilian1.getBuriedness() == 0 && civilian2.getBuriedness() > 0) {
                            return civilian2;
                        } else {
                            if (civilian1.getBuriedness() < civilian2.getBuriedness()) {
                                return civilian1;
                            } else if (civilian1.getBuriedness() > civilian2.getBuriedness()) {
                                return civilian2;
                            }
                        }
                    }
                    if(civilian1.isDamageDefined() && civilian2.isDamageDefined()) {
                        if(civilian1.getDamage() < civilian2.getDamage()) {
                            return civilian1;
                        } else if(civilian1.getDamage() > civilian2.getDamage()) {
                            return civilian2;
                        }
                    }
                }
            }
            return civilian1;
        }
        if(this.checkCivilian(civilian2, true)) {
            return civilian2;
        } else if(this.checkCivilian(civilian1, false)) {
            return civilian1;
        } else if(this.checkCivilian(civilian2, false)) {
            return civilian2;
        }
        return null;
    }

    private boolean checkCivilian(Civilian c, boolean checkOtherValues) {
        if(c != null && c.isHPDefined() && c.isPositionDefined()) {
            if(checkOtherValues && (!c.isDamageDefined() && !c.isBuriednessDefined())) {
                return false;
            }
            return true;
        }
        return false;
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

