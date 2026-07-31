{{- define "litchi.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "litchi.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "litchi.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
