{{/*
Chart label — included on every resource.
*/}}
{{- define "proteinlens.chartLabel" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "proteinlens.labels" -}}
{{ include "proteinlens.chartLabel" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Selector labels for a given component. Call with a dict:
  {{ include "proteinlens.selectorLabels" (dict "component" "neo4j" "root" .) }}
*/}}
{{- define "proteinlens.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}
