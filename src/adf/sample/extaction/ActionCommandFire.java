package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandFire;
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
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionCommandFire extends ExtCommandAction{
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandFire.ACTION_REST;
    private static final int ACTION_MOVE = CommandFire.ACTION_MOVE;
    private static final int ACTION_EXTINGUISH = CommandFire.ACTION_EXTINGUISH;
    private static final int ACTION_REFILL = CommandFire.ACTION_REFILL;
    private static final int ACTION_AUTONOMY = CommandFire.ACTION_AUTONOMY;
    private static final int ACTION_SCOUT = 6;

    private PathPlanning pathPlanning;

    private ExtAction actionFireFighting;
    private ExtAction actionExtMove;

    private int maxWater;

    private int commandType;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;
    
    public ActionCommandFire(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.commandType = ACTION_UNKNOWN;
        switch  (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("ActionCommandFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("ActionCommandFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("ActionCommandFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("ActionCommandFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("ActionCommandFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("ActionCommandFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("ActionCommandFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("ActionCommandFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("ActionCommandFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
        }
    }

    public ActionCommandFire precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionFireFighting.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
        return this;
    }

    public ActionCommandFire resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionFireFighting.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        return this;
    }

    public ActionCommandFire preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionFireFighting.preparate();
        this.actionExtMove.preparate();
        return this;
    }

    public ActionCommandFire updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionFireFighting.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        if(this.isCommandCompleted()) {
            if(this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
            }
            this.commandType = ACTION_UNKNOWN;
        }
        EntityID agentID = agentInfo.getID();
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(command.getToID().getValue() == agentID.getValue()) {
                this.commandType = ACTION_SCOUT;
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
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire) message;
            if(command.getToID().getValue() == agentID.getValue()) {
                this.commandType = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
        return this;
    }

    @Override
    public ExtCommandAction calc() {
        this.result = null;
        switch (this.commandType) {
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
            case ACTION_EXTINGUISH:
                this.result = this.actionFireFighting.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_REFILL:
                this.result = this.actionFireFighting.setTarget(this.target).calc().getAction();
                return this;
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if(targetEntity.getStandardURN() == REFUGE) {
                        FireBrigade agent = (FireBrigade) this.agentInfo.me();
                        if(agent.getDamage() > 0) {
                            EntityID position1 = this.agentInfo.getPosition();
                            if (position1.getValue() != this.target.getValue()) {
                                List<EntityID> path = this.pathPlanning.getResult(position1, this.target);
                                if(path != null) {
                                    this.result = new ActionMove(path);
                                    return this;
                                }
                            }
                            this.result = new ActionRest();
                        } else {
                            this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                        }
                    } else if (targetEntity instanceof Building) {
                        this.result = this.actionFireFighting.setTarget(this.target).calc().getAction();
                    } else if(targetEntity instanceof Road) {
                        this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
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
        switch (this.commandType) {
            case ACTION_REST:
                if (this.worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                    FireBrigade agent = (FireBrigade) this.agentInfo.me();
                    if (agent.getPosition().getValue() == this.target.getValue()) {
                        return (agent.getDamage() == 0);
                    }
                }
                return false;
            case ACTION_MOVE:
                return (this.agentInfo.getPosition().getValue() == this.target.getValue());
            case ACTION_EXTINGUISH:
                return (((Building) this.worldInfo.getEntity(this.target)).getFieryness() >= 4);
            case ACTION_REFILL:
                return (((FireBrigade)this.agentInfo.me()).getWater() == this.maxWater);
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if(targetEntity.getStandardURN() == REFUGE) {
                        FireBrigade agent = (FireBrigade) this.agentInfo.me();
                        this.commandType = agent.getDamage() > 0 ? ACTION_REST : ACTION_REFILL;
                        return this.isCommandCompleted();
                    } else if (targetEntity instanceof Building) {
                        this.commandType = ACTION_EXTINGUISH;
                        return this.isCommandCompleted();
                    } else if(targetEntity instanceof Road) {
                        this.commandType = ACTION_MOVE;
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
