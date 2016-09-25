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
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleVictimSelector extends HumanSelector {

    private EntityID result;

    private EntityID oldPosition;
    private int oldX;
    private int oldY;
    private int sendTime;
    private int commandInterval;

    public SampleVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.result = null;
        this.oldPosition = ai.getID();
        this.oldX = -1;
        this.oldY = -1;
        this.sendTime = 0;
        this.commandInterval = developData.getInteger("ambulance.command.clear.interval", 5);
    }

    @Override
    public HumanSelector updateInfo(MessageManager messageManager) {
        Human agent = (Human)this.agentInfo.me();
        EntityID agentPosition = agent.getPosition();
        if(agentPosition.getValue() == this.oldPosition.getValue()) {
            if (this.equalsPoint(agent.getX(), agent.getY(), this.oldX, this.oldY, 1000)) {
                int currentTime = this.agentInfo.getTime();
                if ((this.sendTime + this.commandInterval) <= currentTime) {
                    this.sendTime = currentTime;
                    messageManager.addMessage(
                            new CommandPolice(true, null, agentPosition, CommandPolice.ACTION_CLEAR)
                    );
                }
            }
        }
        this.oldPosition = agentPosition;
        this.oldX = agent.getX();
        this.oldY = agent.getY();

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
            Clustering clustering = this.moduleManager.getModule("TacticsAmbulance.Clustering");
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
        return this;
    }

    @Override
    public HumanSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public HumanSelector preparate() {
        super.preparate();
        return this;
    }

    private boolean equalsPoint(int p1X, int p1Y, int p2X, int p2Y, int range) {
        return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
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

