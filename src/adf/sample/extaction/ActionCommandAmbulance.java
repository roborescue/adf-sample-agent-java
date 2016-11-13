package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandAmbulance;
import adf.agent.communication.standard.bundle.topdown.CommandScout;
import adf.agent.communication.standard.bundle.topdown.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.extaction.ExtAction;
import adf.component.extaction.ExtCommandAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionCommandAmbulance extends ExtCommandAction {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandAmbulance.ACTION_REST;
    private static final int ACTION_MOVE = CommandAmbulance.ACTION_MOVE;
    private static final int ACTION_RESCUE = CommandAmbulance.ACTION_RESCUE;
    private static final int ACTION_LOAD = CommandAmbulance.ACTION_LOAD;
    private static final int ACTION_UNLOAD = CommandAmbulance.ACTION_UNLOAD;
    private static final int ACTION_AUTONOMY = CommandAmbulance.ACTION_AUTONOMY;
    private static final int ACTION_SCOUT = 6;

    private PathPlanning pathPlanning;

    private ExtAction actionTransport;
    private ExtAction actionExtMove;

    private int type;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    public ActionCommandAmbulance(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.type = ACTION_UNKNOWN;
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("ActionCommandAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionTransport = moduleManager.getExtAction("TacticsCommandAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsCommandAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("ActionCommandAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionTransport = moduleManager.getExtAction("TacticsCommandAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsCommandAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("ActionCommandAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionTransport = moduleManager.getExtAction("TacticsCommandAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsCommandAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
        }
    }

    public ActionCommandAmbulance precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionTransport.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
        return this;
    }

    public ActionCommandAmbulance resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionTransport.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        return this;
    }

    public ActionCommandAmbulance preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionTransport.preparate();
        this.actionExtMove.preparate();
        return this;
    }

    public ActionCommandAmbulance updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionTransport.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        AmbulanceTeam agent = (AmbulanceTeam)agentInfo.me();
        if(this.isCommandCompleted()) {
            if(this.type != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                if(this.type == ACTION_LOAD) {
                    this.type = ACTION_UNLOAD;
                    this.target = null;
                } else {
                    this.type = ACTION_UNKNOWN;
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(command.getToID().getValue() == agent.getID().getValue()) {
                this.type = ACTION_SCOUT;
                this.commanderID = command.getSenderID();
                this.scoutTargets = new HashSet<>();
                this.scoutTargets.addAll(
                        worldInfo.getObjectsInRange(command.getTargetID(), command.getRange())
                                .stream()
                                .filter(e -> e instanceof Area && e.getStandardURN() != REFUGE)
                                .map(AbstractEntity::getID)
                                .collect(Collectors.toList())
                );
                break;
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance) message;
            if(command.getToID().getValue() == agent.getID().getValue()) {
                this.type = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
        return this;
    }

    @Override
    public ActionCommandAmbulance calc() {
        this.result = null;
        switch (this.type) {
            case ACTION_REST:
                EntityID position = this.agentInfo.getPosition();
                if (position.getValue() != this.target.getValue()) {
                    List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                    if(path != null) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                }
                this.result = new ActionRest();
                return this;
            case ACTION_MOVE:
                this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_RESCUE:
                this.result = this.actionTransport.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_LOAD:
                this.result = this.actionTransport.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_UNLOAD:
                this.result = this.actionTransport.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if (targetEntity instanceof Area) {
                        if(this.agentInfo.someoneOnBoard() == null) {
                            this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                        } else {
                            this.result = this.actionTransport.setTarget(this.target).calc().getAction();
                        }
                    }else if (targetEntity instanceof Human) {
                        this.result = this.actionTransport.setTarget(this.target).calc().getAction();
                    }
                }
                return this;
            case ACTION_SCOUT:
                if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
                    return this;
                }
                this.pathPlanning.setFrom(this.agentInfo.getPosition());
                this.pathPlanning.setDestination(this.scoutTargets);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if(path != null) {
                    this.result = new ActionMove(path);
                }
        }
        return this;
    }

    private boolean isCommandCompleted() {
        switch (this.type) {
            case ACTION_REST:
                if (this.worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                    Human agent = (Human) this.agentInfo.me();
                    if (agent.getPosition().getValue() == this.target.getValue()) {
                        return (agent.getDamage() == 0);
                    }
                }
                return false;
            case ACTION_MOVE:
                return this.agentInfo.getPosition().getValue() == this.target.getValue();
            case ACTION_RESCUE:
                Human human = (Human) this.worldInfo.getEntity(this.target);
                return human.isBuriednessDefined() && human.getBuriedness() == 0 || (human.isHPDefined() && human.getHP() == 0);
            case ACTION_LOAD:
                Human human1 = (Human) this.worldInfo.getEntity(this.target);
                if((human1.isHPDefined() && human1.getHP() == 0)) {
                    return true;
                }
                if(human1.getStandardURN() != CIVILIAN) {
                    this.type = ACTION_RESCUE;
                    return this.isCommandCompleted();
                }
                if (human1.isPositionDefined()) {
                    EntityID position = human1.getPosition();
                    if (this.worldInfo.getEntityIDsOfType(AMBULANCE_TEAM).contains(position)) {
                        return true;
                    } else if (this.worldInfo.getEntity(position).getStandardURN() == REFUGE) {
                        return true;
                    }
                }
                return false;
            case ACTION_UNLOAD:
                StandardEntity entity = this.worldInfo.getEntity(this.target);
                if (entity != null && entity instanceof Area) {
                    if (this.target.getValue() != this.agentInfo.getPosition().getValue()) {
                        return false;
                    }
                }
                return (this.agentInfo.someoneOnBoard() == null);
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if (targetEntity instanceof Area) {
                        this.type = this.agentInfo.someoneOnBoard() == null ? ACTION_MOVE : ACTION_UNLOAD;
                        return this.isCommandCompleted();
                    }else if (targetEntity instanceof Human) {
                        Human h = (Human) targetEntity;
                        if((h.isHPDefined() && h.getHP() == 0)) {
                            return true;
                        }
                        this.type = h.getStandardURN() == CIVILIAN ? ACTION_LOAD : ACTION_RESCUE;
                        return this.isCommandCompleted();
                    }
                }
                return true;
            case ACTION_SCOUT:
                this.scoutTargets.removeAll(this.worldInfo.getChanged().getChangedEntities());
                return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
    }
}
