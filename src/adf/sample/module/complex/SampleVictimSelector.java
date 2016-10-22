package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.communication.standard.bundle.topdown.CommandFire;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanSelector;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleVictimSelector extends HumanSelector {

    private Clustering clustering;

    private EntityID result;

    private int sendTime;
    private int commandInterval;

    public SampleVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
        this.result = null;
        this.sendTime = 0;
        this.commandInterval = developData.getInteger("ambulance.command.clear.interval", 5);
    }

    @Override
    public HumanSelector updateInfo(MessageManager messageManager) {
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.clustering.updateInfo(messageManager);

        int currentTime = this.agentInfo.getTime();
        Human agent = (Human)this.agentInfo.me();
        int agentX = agent.getX();
        int agentY = agent.getY();
        StandardEntity positionEntity = this.worldInfo.getPosition(agent);
        if(positionEntity instanceof Road) {
            Road road = (Road)positionEntity;
            if(road.isBlockadesDefined() && road.getBlockades().size() > 0) {
                for(Blockade blockade : this.worldInfo.getBlockades(road)) {
                    if(!blockade.isApexesDefined()) {
                        continue;
                    }
                    if(this.isInside(agentX, agentY, blockade.getApexes())) {
                        if ((this.sendTime + this.commandInterval) <= currentTime) {
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
        }

        if(this.result != null) {
            StandardEntity entity = this.worldInfo.getEntity(this.result);
            if(entity != null && entity instanceof Human) {
                Human h = (Human)entity;
                if(h.isPositionDefined()) {
                    StandardEntity humanPosition = this.worldInfo.getPosition(h);
                    if(humanPosition instanceof Building && ((Building)humanPosition).isOnFire()) {
                        messageManager.addMessage(
                                new CommandFire(true, null, humanPosition.getID(), CommandFire.ACTION_EXTINGUISH)
                        );
                    }
                }
            }
        }

        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!changedEntities.contains(mc.getAgentID())){
                    MessageUtil.reflectMessage(this.worldInfo, mc);
                }
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mat);
                }
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!changedEntities.contains(mfb.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mfb);
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mpf);
                }
            }
        }
        return this;
    }

    @Override
    public HumanSelector calc() {
        Human transportHuman =  this.agentInfo.someoneOnBoard();
        if(transportHuman != null) {
            this.result = transportHuman.getID();
            return this;
        }
        if(this.result != null) {
            Human target = (Human) this.worldInfo.getEntity(this.result);
            if (!target.isHPDefined() || target.getHP() == 0) {
                this.result = null;
            } else if(!target.isPositionDefined()) {
                this.result = null;
            } else {
                StandardEntity position = this.worldInfo.getPosition(target);
                StandardEntityURN positionURN = position.getStandardURN();
                if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM) {
                    this.result = null;
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
    public HumanSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.clustering.precompute(precomputeData);
        return this;
    }

    @Override
    public HumanSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.clustering.resume(precomputeData);
        return this;
    }

    @Override
    public HumanSelector preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
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
}

