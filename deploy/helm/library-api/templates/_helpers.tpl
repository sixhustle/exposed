{{/* 이름·라벨을 한 곳에서 만들어 모든 리소스가 같은 규칙을 쓰게 한다 */}}
{{- define "library-api.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "library-api.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "library-api.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* 모든 리소스에 붙는 공통 라벨 (쿠버네티스 권장 라벨) */}}
{{- define "library-api.labels" -}}
app.kubernetes.io/name: {{ include "library-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{/* 파드를 고르는 셀렉터 (Deployment의 selector는 나중에 바꿀 수 없으니 최소한만 넣는다) */}}
{{- define "library-api.selectorLabels" -}}
app.kubernetes.io/name: {{ include "library-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
