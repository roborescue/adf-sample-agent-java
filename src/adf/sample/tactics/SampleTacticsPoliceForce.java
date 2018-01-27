package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.police.ActionClear;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.debug.WorldViewLauncher;
import adf.component.centralized.CommandExecutor;
import adf.component.communication.CommunicationMessage;
import adf.component.extaction.ExtAction;
import adf.component.module.complex.RoadDetector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPoliceForce;
import adf.sample.tactics.utils.MessageTool;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;
import java.util.Objects;


public class SampleTacticsPoliceForce extends TacticsPoliceForce
{
    private int clearDistance;

    private RoadDetector roadDetector;
    private Search search;

    private ExtAction actionExtClear;
    private ExtAction actionExtMove;

    private CommandExecutor<CommandPolice> commandExecutorPolice;
    private CommandExecutor<CommandScout> commandExecutorScout;

    private MessageTool messageTool;

    private CommunicationMessage recentCommand;

	private Boolean isVisualDebug;

//	private WorldViewer worldViewer;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData)
    {
        messageManager.setChannelSubscriber(moduleManager.getChannelSubscriber("MessageManager.PlatoonChannelSubscriber", "adf.sample.module.comm.SampleChannelSubscriber"));
        messageManager.setMessageCoordinator(moduleManager.getMessageCoordinator("MessageManager.PlatoonMessageCoordinator", "adf.sample.module.comm.SampleMessageCoordinator"));

        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );

        this.messageTool = new MessageTool(scenarioInfo, developData);

        this.isVisualDebug = (scenarioInfo.isDebugMode()
                && moduleManager.getModuleConfig().getBooleanValue("VisualDebug", false));
        // init value
        this.clearDistance = scenarioInfo.getClearRepairDistance();
        this.recentCommand = null;
        // init Algorithm Module & ExtAction
        switch  (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
            case PRECOMPUTED:
                this.search = moduleManager.getModule("TacticsPoliceForce.Search", "adf.sample.module.complex.SampleSearch");
                this.roadDetector = moduleManager.getModule("TacticsPoliceForce.RoadDetector", "adf.sample.module.complex.SampleRoadDetector");
                this.actionExtClear = moduleManager.getExtAction("TacticsPoliceForce.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                this.actionExtMove = moduleManager.getExtAction("TacticsPoliceForce.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                this.commandExecutorPolice = moduleManager.getCommandExecutor("TacticsPoliceForce.CommandExecutorPolice", "adf.sample.centralized.CommandExecutorPolice");
                this.commandExecutorScout = moduleManager.getCommandExecutor("TacticsPoliceForce.CommandExecutorScout", "adf.sample.centralized.CommandExecutorScoutPolice");
                break;
            case NON_PRECOMPUTE:
                this.search = moduleManager.getModule("TacticsPoliceForce.Search", "adf.sample.module.complex.SampleSearch");
                this.roadDetector = moduleManager.getModule("TacticsPoliceForce.RoadDetector", "adf.sample.module.complex.SampleRoadDetector");
                this.actionExtClear = moduleManager.getExtAction("TacticsPoliceForce.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                this.actionExtMove = moduleManager.getExtAction("TacticsPoliceForce.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                this.commandExecutorPolice = moduleManager.getCommandExecutor("TacticsPoliceForce.CommandExecutorPolice", "adf.sample.centralized.CommandExecutorPolice");
                this.commandExecutorScout = moduleManager.getCommandExecutor("TacticsPoliceForce.CommandExecutorScout", "adf.sample.centralized.CommandExecutorScoutPolice");
                break;
        }
        registerModule(this.search);
        registerModule(this.roadDetector);
        registerModule(this.actionExtClear);
        registerModule(this.actionExtMove);
        registerModule(this.commandExecutorPolice);
        registerModule(this.commandExecutorScout);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData)
    {
        modulesPrecompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData)
    {
        modulesResume(precomputeData);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData)
    {
        modulesPreparate();

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData)
    {
        this.messageTool.reflectMessage(agentInfo, worldInfo, scenarioInfo, messageManager);
        this.messageTool.sendRequestMessages(agentInfo, worldInfo, scenarioInfo, messageManager);
        this.messageTool.sendInformationMessages(agentInfo, worldInfo, scenarioInfo, messageManager);

        modulesUpdateInfo(messageManager);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
        PoliceForce agent = (PoliceForce) agentInfo.me();
        EntityID agentID = agent.getID();
        // command
        for (CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class))
        {
            CommandScout command = (CommandScout) message;
            if (command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue())
            {
                this.recentCommand = command;
                this.commandExecutorScout.setCommand(command);
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class))
        {
            CommandPolice command = (CommandPolice) message;
            if (command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue())
            {
                this.recentCommand = command;
                this.commandExecutorPolice.setCommand(command);
            }
        }
//        worldViewer.showTimestep(0);
        if (this.recentCommand != null)
        {
            Action action = null;
            if (this.recentCommand.getClass() == CommandPolice.class)
            {
                action = this.commandExecutorPolice.calc().getAction();
            }
            else if (this.recentCommand.getClass() == CommandScout.class)
            {
                action = this.commandExecutorScout.calc().getAction();
            }
            if (action != null)
            {
                this.sendActionMessage(worldInfo, messageManager, agent, action);
                return action;
            }
        }
        // autonomous
        EntityID target = this.roadDetector.calc().getTarget();
        Action action = this.actionExtClear.setTarget(target).calc().getAction();
        if (action != null)
        {
            this.sendActionMessage(worldInfo, messageManager, agent, action);
            return action;
        }

        target = this.search.calc().getTarget();
        action = this.actionExtClear.setTarget(target).calc().getAction();
        if(action != null)
        {
            this.sendActionMessage(worldInfo, messageManager, agent, action);
            return action;
        }

        messageManager.addMessage(
                new MessagePoliceForce(true, agent, MessagePoliceForce.ACTION_REST, agent.getPosition())
        );
        return new ActionRest();
    }

    private void sendActionMessage(WorldInfo worldInfo, MessageManager messageManager, PoliceForce policeForce, Action action)
    {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if (actionClass == ActionMove.class)
        {
            List<EntityID> path = ((ActionMove)action).getPath();
            actionIndex = MessagePoliceForce.ACTION_MOVE;
            if (path.size() > 0)
            {
                target = path.get(path.size() - 1);
            }
        }
        else if (actionClass == ActionClear.class)
        {
            actionIndex = MessagePoliceForce.ACTION_CLEAR;
            ActionClear ac = (ActionClear)action;
            target = ac.getTarget();
            if (target == null)
            {
                for (StandardEntity entity : worldInfo.getObjectsInRange(ac.getPosX(), ac.getPosY(), this.clearDistance))
                {
                    if (entity.getStandardURN() == StandardEntityURN.BLOCKADE)
                    {
                        target = entity.getID();
                        break;
                    }
                }
            }
        }
        else if (actionClass == ActionRest.class)
        {
            actionIndex = MessagePoliceForce.ACTION_REST;
            target = policeForce.getPosition();
        }

        if (actionIndex != -1)
        {
            messageManager.addMessage(new MessagePoliceForce(true, policeForce, actionIndex, target));
        }
    }
}
