/**
 * GraphEngine — Obsidian 风格关系图谱核心引擎
 *
 * PixiJS 8 渲染 + d3-force 3 物理模拟
 * 职责：渲染、物理、Camera、交互
 */
import { Application, Container, Graphics, Text, type ApplicationOptions } from "pixi.js";
import {
  forceSimulation,
  forceManyBody,
  forceLink,
  forceCenter,
  forceCollide,
  type SimulationNodeDatum,
  type SimulationLinkDatum,
  type Simulation,
} from "d3-force";

/* ================================================================
 * 类型
 * ================================================================ */

export interface GraphNodeInput {
  id: string;
  label: string;
  type: string;
  domain: string;
  degree: number;
  size: number;
  color: string;
  refId?: string;
  updatedAt?: string;
  summary?: string;
}

export interface GraphEdgeInput {
  id: string;
  source: string;
  target: string;
  rel: string;
}

export interface ForcesConfig {
  center: number;
  repulsion: number;
  attraction: number;
  linkLength: number;
}

export interface DisplayConfig {
  showLabels: boolean;
  textOpacity: number;
  nodeScale: number;
  linkWidth: number;
}

interface SimNode extends SimulationNodeDatum {
  id: string;
  label: string;
  type: string;
  domain: string;
  degree: number;
  size: number;
  color: string;
  refId?: string;
  summary?: string;
  updatedAt?: string;
  _originX?: number;
  _originY?: number;
}

interface SimEdge extends SimulationLinkDatum<SimNode> {
  id: string;
  rel: string;
}

export interface GraphEngineEvents {
  onHover: (id: string | null) => void;
  onSelect: (id: string | null) => void;
  onStats: (s: { nodes: number; edges: number }) => void;
}

/* ================================================================
 * GraphEngine
 * ================================================================ */

export class GraphEngine {
  private app: Application;
  private world!: Container;
  private edgeCanvas!: HTMLCanvasElement;
  private edgeCtx!: CanvasRenderingContext2D;
  private nodeC!: Container;
  private glowG!: Graphics;
  private labelC!: Container;

  private simNodes: SimNode[] = [];
  private simEdges: SimEdge[] = [];
  private sim!: Simulation<SimNode, SimEdge>;

  private adj = new Map<string, Set<string>>();
  private nodeMap = new Map<string, SimNode>();
  private pixiNodes = new Map<string, Graphics>();
  private pixiLabels = new Map<string, Text>();

  private hoveredId: string | null = null;
  private selectedId: string | null = null;
  private searchNeedle = "";

  private cam = { x: 0, y: 0, zoom: 1 };
  private camTarget = { x: 0, y: 0, zoom: 1 };
  private camAnimating = false;

  private forces: ForcesConfig = { center: 40, repulsion: 20000, attraction: 80, linkLength: 100 };
  private display: DisplayConfig = { showLabels: true, textOpacity: 0.5, nodeScale: 1, linkWidth: 1 };

  private grabbing = false;
  private grabNode: SimNode | null = null;
  private panning = false;
  private panLast = { x: 0, y: 0 };
  private dragOffset = { x: 0, y: 0 };
  private lastPinchDist = 0;
  private lastMouse = { x: 0, y: 0 };

  private growthTimer = 0;
  private events: GraphEngineEvents;
  private destroyed = false;
  private mounted = false;

  constructor(events: GraphEngineEvents) {
    this.events = events;
    this.app = new Application();
  }

  /* ---- 生命周期 ---- */

  async mount(container: HTMLElement): Promise<void> {
    if (this.mounted) return;

    const w = container.clientWidth || 800;
    const h = container.clientHeight || 600;

    try {
      await this.app.init({
        width: w,
        height: h,
        resizeTo: container,
        backgroundAlpha: 0,
        antialias: true,
        resolution: window.devicePixelRatio || 1,
        autoDensity: true,
      } as Partial<ApplicationOptions>);
    } catch (err) {
      console.error("[GraphEngine] init failed:", err);
      return;
    }

    container.appendChild(this.app.canvas as HTMLCanvasElement);

    // 创建 Canvas 2D 用于画连线（比 PixiJS Graphics 更可靠）
    this.edgeCanvas = document.createElement("canvas");
    this.edgeCanvas.style.cssText = "position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:1;";
    this.edgeCtx = this.edgeCanvas.getContext("2d")!;
    this.edgeCanvas.width = w * (window.devicePixelRatio || 1);
    this.edgeCanvas.height = h * (window.devicePixelRatio || 1);
    container.insertBefore(this.edgeCanvas, this.app.canvas as HTMLCanvasElement);
    // 确保 PixiJS canvas 在最上层，节点盖住连线
    (this.app.canvas as HTMLCanvasElement).style.position = "relative";
    (this.app.canvas as HTMLCanvasElement).style.zIndex = "2";

    this.world = new Container();
    this.nodeC = new Container();
    this.glowG = new Graphics();
    this.labelC = new Container();

    this.world.addChild(this.glowG);
    this.world.addChild(this.nodeC);
    this.world.addChild(this.labelC);
    this.app.stage.addChild(this.world);

    this.cam.x = w / 2;
    this.cam.y = h / 2;
    this.camTarget.x = w / 2;
    this.camTarget.y = h / 2;
    this.world.position.set(this.cam.x, this.cam.y);
    this.world.scale.set(this.cam.zoom);

    this.bindEvents(container);
    this.mounted = true;
    this.app.ticker.add(this.tick);
  }

  /* ---- 数据 ---- */

  private clear(): void {
    this.sim?.stop();
    this.pixiNodes.forEach((g) => { this.nodeC.removeChild(g); g.destroy(); });
    this.pixiLabels.forEach((t) => { this.labelC.removeChild(t); t.destroy(); });
    this.pixiNodes.clear();
    this.pixiLabels.clear();
    this.nodeMap.clear();
    this.glowG.clear();
    if (this.edgeCtx) {
      const dpr = window.devicePixelRatio || 1;
      this.edgeCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.edgeCtx.clearRect(0, 0, this.edgeCanvas.width / dpr, this.edgeCanvas.height / dpr);
    }
    this.simNodes = [];
    this.simEdges = [];
    this.hoveredId = null;
    this.selectedId = null;
  }

  setData(nodes: GraphNodeInput[], edges: GraphEdgeInput[]): void {
    this.clear();
    this.buildAdj(nodes, edges);
    this.simNodes = nodes.map((n) => ({
      id: n.id, label: n.label, type: n.type, domain: n.domain,
      degree: n.degree, size: n.size, color: n.color,
      refId: n.refId, summary: n.summary, updatedAt: n.updatedAt,
      x: 0, y: 0, vx: 0, vy: 0,
    }));
    this.simEdges = edges.map((e) => ({ ...e, source: e.source, target: e.target })) as SimEdge[];
    this.nodeMap.clear();
    this.simNodes.forEach((n) => this.nodeMap.set(n.id, n));
    this.simNodes.forEach((n) => {
      n._originX = n.x;
      n._originY = n.y;
    });
    this.initSim();
    this.createPixiNodes();
    this.events.onStats({ nodes: this.simNodes.length, edges: this.simEdges.length });
  }

  private buildAdj(nodes: GraphNodeInput[], edges: GraphEdgeInput[]): void {
    this.adj.clear();
    nodes.forEach((n) => this.adj.set(n.id, new Set()));
    edges.forEach((e) => {
      this.adj.get(e.source)?.add(e.target);
      this.adj.get(e.target)?.add(e.source);
    });
  }

  /* ---- d3-force ---- */

  private initSim(): void {
    const n = this.simNodes.length || 1;
    // 有连接的节点放在外圈，孤立节点放在内圈
    const connected = this.simNodes.filter((nd) => nd.degree > 0);
    const isolated = this.simNodes.filter((nd) => nd.degree === 0);
    const outerR = Math.min(250, Math.sqrt(n) * 35);
    const innerR = Math.min(80, Math.sqrt(n) * 10);

    connected.forEach((node, i) => {
      const angle = (2 * Math.PI * i) / (connected.length || 1);
      node.x = Math.cos(angle) * outerR;
      node.y = Math.sin(angle) * outerR;
    });
    isolated.forEach((node, i) => {
      const angle = (2 * Math.PI * i) / (isolated.length || 1);
      node.x = Math.cos(angle) * innerR;
      node.y = Math.sin(angle) * innerR;
    });

    // 用极弱的力做微调，不让节点飞远
    this.sim = forceSimulation<SimNode, SimEdge>(this.simNodes)
      .force("link", forceLink<SimNode, SimEdge>(this.simEdges)
        .id((d) => d.id)
        .distance(50)
        .strength(0.2))
      .force("charge", forceManyBody<SimNode>()
        .strength(-30)
        .distanceMax(200))
      .force("center", forceCenter<SimNode>(0, 0).strength(0.05))
      .force("collide", forceCollide<SimNode>()
        .radius((d) => d.size * 0.5 + 5))
      .alphaDecay(0.05)
      .velocityDecay(0.8)
      .on("tick", () => {});
  }

  /* ---- PixiJS 节点 ---- */

  private createPixiNodes(): void {
    this.simNodes.forEach((n) => {
      const g = new Graphics();
      g.circle(0, 0, 1).fill({ color: 0xffffff, alpha: 0 });
      g.eventMode = "static";
      g.cursor = "pointer";
      g.on("pointerover", () => this.onNodeHover(n.id));
      g.on("pointerout", () => this.onNodeHover(null));
      g.on("pointerdown", (e: any) => this.onNodeDown(n, e));
      this.nodeC.addChild(g);
      this.pixiNodes.set(n.id, g);

      const t = new Text({
        text: n.label,
        style: { fontSize: 10, fill: "#8a8a8d", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif" },
      });
      t.anchor.set(0.5, 0);
      t.alpha = this.display.textOpacity;
      this.labelC.addChild(t);
      this.pixiLabels.set(n.id, t);
    });
  }

  /* ---- 渲染 ---- */

  private tick = (): void => {
    if (this.destroyed) return;
    this.lerpCam();
    this.drawEdges();
    this.drawNodes();
    this.drawLabels();
    this.drawGlow();
    this.updateWorld();
  };

  private updateWorld(): void {
    this.world.position.set(this.cam.x, this.cam.y);
    this.world.scale.set(this.cam.zoom);
  }

  private drawEdges(): void {
    const ctx = this.edgeCtx;
    const canvas = this.edgeCanvas;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.width / dpr;
    const h = canvas.height / dpr;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    // 应用 camera 变换
    ctx.save();
    ctx.translate(this.cam.x, this.cam.y);
    ctx.scale(this.cam.zoom, this.cam.zoom);

    const lw = this.display.linkWidth;
    const cid = this.hoveredId ?? this.selectedId;
    const nb = cid ? this.adj.get(cid) : undefined;
    const dragId = this.grabbing ? this.grabNode?.id : null;
    const focusId = dragId || cid;
    const hasFocus = !!(focusId || this.searchNeedle);

    ctx.lineCap = "round";

    for (const e of this.simEdges) {
      const sn = e.source as SimNode;
      const tn = e.target as SimNode;
      const sx = sn.x, sy = sn.y, tx = tn.x, ty = tn.y;
      if (sx == null || sy == null || tx == null || ty == null) continue;

      let alpha = 0.25;
      let lineW = 0.8 * lw;
      let r = 138, g_ = 138, b = 141; // #8a8a8d

      if (hasFocus) {
        if (sn.id === focusId || tn.id === focusId) {
          alpha = 0.85; lineW = 1.5 * lw; r = 255; g_ = 255; b = 255;
        } else if (focusId && (this.adj.get(focusId)?.has(sn.id) || this.adj.get(focusId)?.has(tn.id))) {
          alpha = 0.5;
        } else {
          alpha = 0.04;
        }
      }

      if (this.searchNeedle) {
        const sm = this.nodeMap.get(sn.id);
        const tm = this.nodeMap.get(tn.id);
        const sHit = sm?.label.toLowerCase().includes(this.searchNeedle) ?? false;
        const tHit = tm?.label.toLowerCase().includes(this.searchNeedle) ?? false;
        if (sHit && tHit) { alpha = 0.8; r = 255; g_ = 255; b = 255; }
        else if (sHit || tHit) { alpha = 0.35; }
        else { alpha = 0.03; }
      }

      // 连线停在节点边缘，不画到圆心
      const dx = tx - sx, dy = ty - sy;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      const nr = (sn.size + tn.size) * 0.25 * this.display.nodeScale;
      const rA = Math.min(nr, dist * 0.4);
      const rB = Math.min(nr, dist * 0.4);
      const ux = dx / dist, uy = dy / dist;

      ctx.beginPath();
      ctx.moveTo(sx + ux * rA, sy + uy * rA);
      ctx.lineTo(tx - ux * rB, ty - uy * rB);
      ctx.strokeStyle = `rgba(${r},${g_},${b},${alpha})`;
      ctx.lineWidth = lineW;
      ctx.stroke();
    }

    ctx.restore();
  }

  private drawNodes(): void {
    const z = this.cam.zoom;

    for (const n of this.simNodes) {
      const g = this.pixiNodes.get(n.id);
      if (!g || n.x == null) continue;

      g.position.set(n.x ?? 0, n.y ?? 0);
      g.clear();

      let alpha = 0.88;
      let scale = 1;
      let borderColor = 0x000000;
      let borderAlpha = 0;
      let borderW = 0;

      // 拖拽中：拖拽节点高亮，其余 dim
      if (this.grabbing && this.grabNode) {
        if (n.id === this.grabNode.id) {
          scale = 1.2;
          alpha = 1;
          borderW = 2;
          borderColor = 0xffffff;
          borderAlpha = 0.8;
        } else {
          const nb = this.adj.get(this.grabNode.id);
          alpha = nb?.has(n.id) ? 0.9 : 0.15;
        }
      } else if (this.hoveredId === n.id) {
        scale = 1.18;
        alpha = 1;
        borderW = 2;
        borderColor = 0xffffff;
        borderAlpha = 0.6;
      } else if (this.selectedId === n.id) {
        scale = 1.12;
        alpha = 1;
        borderW = 2;
        borderColor = 0xffffff;
        borderAlpha = 0.7;
      } else if (this.hoveredId || this.selectedId) {
        const cid = this.hoveredId ?? this.selectedId!;
        const nb = this.adj.get(cid);
        if (nb?.has(n.id)) {
          alpha = 0.95;
        } else {
          alpha = 0.12;
        }
      }

      if (!this.grabbing && this.searchNeedle) {
        const hit = n.label.toLowerCase().includes(this.searchNeedle);
        if (hit) { alpha = 1; scale = Math.max(scale, 1.1); }
        else if (this.hoveredId || this.selectedId) { /* keep existing */ }
        else { alpha = 0.08; }
      }

      const r = n.size * 0.5 * this.display.nodeScale * scale;
      const col = typeof n.color === "string" && n.color.startsWith("#")
        ? parseInt(n.color.replace("#", ""), 16) : 0x8a8a8d;

      g.circle(0, 0, r).fill({ color: col, alpha });
      if (borderW > 0) {
        g.circle(0, 0, r).stroke({ width: borderW, color: borderColor, alpha: borderAlpha });
      }
    }
  }

  private drawGlow(): void {
    const g = this.glowG;
    g.clear();
    if (!this.hoveredId) return;
    const n = this.nodeMap.get(this.hoveredId);
    if (n?.x == null) return;

    const r = n.size * 0.5 * this.display.nodeScale;
    const glowR = r * 3;
    const col = typeof n.color === "string" && n.color.startsWith("#")
      ? parseInt(n.color.replace("#", ""), 16) : 0x8a8a8d;

    g.circle(n.x ?? 0, n.y ?? 0, glowR).fill({ color: col, alpha: 0.08 });
    g.circle(n.x ?? 0, n.y ?? 0, glowR * 0.6).fill({ color: col, alpha: 0.05 });
  }

  private drawLabels(): void {
    const z = this.cam.zoom;
    const show = this.display.showLabels;
    const baseOp = this.display.textOpacity;
    const invZ = 1 / z;

    for (const n of this.simNodes) {
      const t = this.pixiLabels.get(n.id);
      if (!t || n.x == null) continue;

      let vis = show && n.degree >= 2;
      let op = baseOp;
      let fw: "400" | "500" | "600" | "700" = "400";
      let fs = 10;
      let col = "#8a8a8d";

      // 拖拽中：拖拽节点标签高亮
      if (this.grabbing && this.grabNode) {
        if (n.id === this.grabNode.id) {
          vis = true; op = 1; fw = "700"; fs = 12; col = "#ededee";
        } else {
          const nb = this.adj.get(this.grabNode.id);
          if (nb?.has(n.id)) { vis = true; op = 0.85; fw = "500"; col = "#c0c0c3"; }
          else { op = 0.08; }
        }
      } else if (this.hoveredId === n.id || this.selectedId === n.id) {
        vis = true;
        op = 1;
        fw = this.hoveredId === n.id ? "700" : "600";
        fs = 12;
        col = "#ededee";
      } else if (this.hoveredId || this.selectedId) {
        const cid = this.hoveredId ?? this.selectedId!;
        const nb = this.adj.get(cid);
        if (nb?.has(n.id)) {
          vis = true;
          op = 0.85;
          fw = "500";
          fs = 10.5;
          col = "#c0c0c3";
        } else {
          op = 0.08;
        }
      }

      if (!this.grabbing && this.searchNeedle) {
        const hit = n.label.toLowerCase().includes(this.searchNeedle);
        if (hit) { vis = true; op = 1; fw = "600"; col = "#ededee"; }
      }

      t.text = n.label;
      const nr = n.size * 0.5 * this.display.nodeScale;
      t.position.set(n.x ?? 0, (n.y ?? 0) + nr + 4);
      t.scale.set(invZ);
      t.style.fontSize = fs;
      t.style.fill = col;
      t.style.fontWeight = fw;
      t.alpha = vis ? op : 0;
    }
  }

  /* ---- Hover / Select / Search ---- */

  private onNodeHover(id: string | null): void {
    if (this.grabbing) return;
    this.hoveredId = id;
    if (id) {
      const n = this.nodeMap.get(id);
      if (n) this.app.canvas.style.cursor = "pointer";
    } else {
      this.app.canvas.style.cursor = "pointer";
    }
    this.events.onHover(id);
  }

  private onNodeDown(n: SimNode, e: any): void {
    e.stopPropagation?.();
    this.grabbing = true;
    this.grabNode = n;
    this.hoveredId = null;
    this.pixiNodes.forEach((g) => { g.eventMode = "none"; });
    n._originX = n.x ?? 0;
    n._originY = n.y ?? 0;
    n.fx = n.x;
    n.fy = n.y;
    this.sim.alphaTarget(0.1).restart();
    this.app.canvas.style.cursor = "pointer";

    const onMove = (ev: PointerEvent) => {
      if (!this.grabbing || !this.grabNode) return;
      const pt = this.screenToWorld(ev.clientX, ev.clientY);
      this.grabNode.fx = pt.x;
      this.grabNode.fy = pt.y;
      this.sim.tick();
    };

    const onUp = () => {
      if (this.grabNode) {
        this.grabNode.fx = null;
        this.grabNode.fy = null;
      }
      this.grabbing = false;
      this.grabNode = null;
      this.pixiNodes.forEach((g) => { g.eventMode = "static"; });
      this.sim.alphaTarget(0);
      this.app.canvas.style.cursor = "pointer";
      this.events.onSelect(n.id);
      this.selectedId = n.id;
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };

    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  }

  select(id: string | null): void {
    this.selectedId = id;
    // 不移动相机，保持图谱在中心
  }

  getSelectedNode(): { id: string; label: string; type: string; domain: string; degree: number; refId?: string; summary?: string; updatedAt?: string } | null {
    if (!this.selectedId) return null;
    const n = this.nodeMap.get(this.selectedId);
    if (!n) return null;
    return { id: n.id, label: n.label, type: n.type, domain: n.domain, degree: n.degree, refId: n.refId, summary: n.summary, updatedAt: n.updatedAt };
  }

  search(q: string): void {
    this.searchNeedle = q.trim().toLowerCase();
    // 不移动相机，搜索只做高亮
  }

  /* ---- Camera ---- */

  private lerpCam(): void {
    if (!this.camAnimating) return;
    const e = 0.12;
    this.cam.x += (this.camTarget.x - this.cam.x) * e;
    this.cam.y += (this.camTarget.y - this.cam.y) * e;
    this.cam.zoom += (this.camTarget.zoom - this.cam.zoom) * e;
    if (
      Math.abs(this.cam.x - this.camTarget.x) < 0.5 &&
      Math.abs(this.cam.y - this.camTarget.y) < 0.5 &&
      Math.abs(this.cam.zoom - this.camTarget.zoom) < 0.001
    ) {
      this.cam.x = this.camTarget.x;
      this.cam.y = this.camTarget.y;
      this.cam.zoom = this.camTarget.zoom;
      this.camAnimating = false;
    }
  }

  private camAnimateTo(x: number, y: number, zoom?: number): void {
    this.camTarget.x = x;
    this.camTarget.y = y;
    if (zoom !== undefined) this.camTarget.zoom = zoom;
    this.camAnimating = true;
  }

  fitView(pad = 50): void {
    if (!this.simNodes.length) return;
    const w = this.app.screen.width;
    const h = this.app.screen.height;
    if (!w || !h) return;

    // 只调 zoom，相机固定在画布中心
    const cx = w / 2;
    const cy = h / 2;

    let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
    for (const n of this.simNodes) {
      const nx = n.x ?? 0, ny = n.y ?? 0;
      x1 = Math.min(x1, nx); y1 = Math.min(y1, ny);
      x2 = Math.max(x2, nx); y2 = Math.max(y2, ny);
    }
    if (!isFinite(x1)) return;
    const gw = (x2 - x1) || 1;
    const gh = (y2 - y1) || 1;
    const z = Math.max(0.1, Math.min((w - pad * 2) / gw, (h - pad * 2) / gh, 2.5));

    this.camTarget.x = cx;
    this.camTarget.y = cy;
    this.camTarget.zoom = z;
    this.camAnimating = true;
  }

  zoomBy(factor: number, cx?: number, cy?: number): void {
    const nz = Math.min(Math.max(this.camTarget.zoom * factor, 0.1), 5);
    if (cx !== undefined && cy !== undefined) {
      const wx = (cx - this.cam.x) / this.cam.zoom;
      const wy = (cy - this.cam.y) / this.cam.zoom;
      this.camTarget.x = cx - wx * nz;
      this.camTarget.y = cy - wy * nz;
    }
    this.camTarget.zoom = nz;
    this.camAnimating = true;
  }

  private screenToWorld(sx: number, sy: number): { x: number; y: number } {
    const rect = (this.app.canvas as HTMLCanvasElement).getBoundingClientRect();
    const cx = sx - rect.left;
    const cy = sy - rect.top;
    return {
      x: (cx - this.cam.x) / this.cam.zoom,
      y: (cy - this.cam.y) / this.cam.zoom,
    };
  }

  /* ---- 事件绑定 ---- */

  private bindEvents(el: HTMLElement): void {
    const c = this.app.canvas as HTMLCanvasElement;
    let panMoved = false;
    c.addEventListener("pointerdown", (e: PointerEvent) => {
      if (this.grabbing) return;
      this.panning = true;
      panMoved = false;
      this.panLast = { x: e.clientX, y: e.clientY };
      c.style.cursor = "pointer";
    });

    window.addEventListener("pointermove", (e: PointerEvent) => {
      this.lastMouse = { x: e.clientX, y: e.clientY };
      if (this.panning) {
        panMoved = true;
        this.cam.x += e.clientX - this.panLast.x;
        this.cam.y += e.clientY - this.panLast.y;
        this.camTarget.x = this.cam.x;
        this.camTarget.y = this.cam.y;
        this.panLast = { x: e.clientX, y: e.clientY };
        this.camAnimating = false;
      }
    });

    window.addEventListener("pointerup", () => {
      if (this.panning && !panMoved) {
        // 点击空白处，取消高亮
        this.hoveredId = null;
        this.selectedId = null;
        this.events.onSelect(null);
        this.events.onHover(null);
      }
      this.panning = false;
      if (!this.grabbing) c.style.cursor = "pointer";
    });

    c.addEventListener("dblclick", () => this.fitView());

    c.addEventListener("wheel", (e: WheelEvent) => {
      e.preventDefault();
      const rect = c.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      const f = e.deltaY > 0 ? 0.9 : 1.1;
      this.zoomBy(f, cx, cy);
    }, { passive: false });

    c.addEventListener("pointerleave", () => { this.onNodeHover(null); });

    // Touch
    c.addEventListener("touchstart", (e: TouchEvent) => {
      if (e.touches.length === 1) {
        this.panning = true;
        this.panLast = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      } else if (e.touches.length === 2) {
        this.lastPinchDist = this.pinches(e.touches);
      }
    }, { passive: true });

    c.addEventListener("touchmove", (e: TouchEvent) => {
      e.preventDefault();
      if (e.touches.length === 1 && this.panning) {
        const t = e.touches[0];
        this.cam.x += t.clientX - this.panLast.x;
        this.cam.y += t.clientY - this.panLast.y;
        this.camTarget.x = this.cam.x;
        this.camTarget.y = this.cam.y;
        this.panLast = { x: t.clientX, y: t.clientY };
        this.camAnimating = false;
      } else if (e.touches.length === 2) {
        const d = this.pinches(e.touches);
        if (this.lastPinchDist) this.zoomBy(d / this.lastPinchDist);
        this.lastPinchDist = d;
      }
    }, { passive: false });

    c.addEventListener("touchend", () => { this.panning = false; this.lastPinchDist = 0; });

    // Resize
    const ro = new ResizeObserver(() => {
      if (this.destroyed) return;
      const w = el.clientWidth;
      const h = el.clientHeight;
      if (w > 0 && h > 0) {
        this.app.renderer.resize(w, h);
        const dpr = window.devicePixelRatio || 1;
        this.edgeCanvas.width = w * dpr;
        this.edgeCanvas.height = h * dpr;
      }
    });
    ro.observe(el);
    (this as any)._ro = ro;
  }

  private pinches(ts: TouchList): number {
    const dx = ts[0].clientX - ts[1].clientX;
    const dy = ts[0].clientY - ts[1].clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /* ---- 公共 API ---- */

  setForces(f: ForcesConfig): void {
    this.forces = f;
    if (!this.sim) return;
    const link = this.sim.force("link") as any;
    if (link) { link.distance(f.linkLength).strength(f.attraction / 200); }
    const charge = this.sim.force("charge") as any;
    if (charge) charge.strength(-f.repulsion);
    const center = this.sim.force("center") as any;
    if (center) center.strength(f.center / 200);
    this.sim.alpha(0.3).restart();
  }

  setDisplay(d: DisplayConfig): void {
    this.display = d;
  }

  replayGrowth(): void {
    this.simNodes.forEach((n) => {
      n.x = (Math.random() - 0.5) * 300;
      n.y = (Math.random() - 0.5) * 300;
      n.vx = 0;
      n.vy = 0;
    });
    this.sim.alpha(1).restart();
    setTimeout(() => this.fitView(), 1200);
  }

  getStats(): { nodes: number; edges: number } {
    return { nodes: this.simNodes.length, edges: this.simEdges.length };
  }

  destroy(): void {
    this.destroyed = true;
    if (this.growthTimer) clearTimeout(this.growthTimer);
    this.sim?.stop();
    (this as any)._ro?.disconnect();
    this.app.destroy(true, { children: true });
  }
}
